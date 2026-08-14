package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.ceres.path.PathType;
import com.ceres.path.Waypoint;
import com.playerapi.hud.HudElement;
import com.playerapi.hud.HudManager;
import com.playerapi.hud.HudTransform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

public class BotHudRenderer {

    /** Owner namespace for the shared HUD editor. */
    public static final String HUD_OWNER = "ceres";

    private static final int BG          = 0xC0000000;
    private static final int HEADER_TINT = 0x18FFFFFF;
    private static final int DIVIDER     = 0x30FFFFFF;
    private static final int LABEL_COL   = 0xFF666666;
    private static final int VALUE_COL   = 0xFFCCCCCC;
    private static final int LOG_BG      = 0x90000000;
    private static final int HINT_COL    = 0x88888888;

    // Panel is drawn from local origin (0,0); screen position + scale come from the HudTransform.
    private static final int PX      = 0;
    private static final int PY      = 0;
    private static final int PW      = 210;
    private static final int LOG_PW  = 310;
    private static final int ACCENT  = 3;
    private static final int HEADER  = 13;
    private static final int PAD     = 5;
    private static final int LINE    = 11;
    private static final int LX_OFF  = 7;
    private static final int VX_OFF  = 60;

    /** Last computed main-panel height, cached for the editor's outline/hit-box. */
    private static int lastPanelHeight = HEADER + 1 + PAD + 5 * LINE + PAD;
    /** Last computed log-panel height, cached for the editor's outline/hit-box. */
    private static int lastLogHeight = HEADER + 1 + 5 * 9 + 4;
    /** Max log lines shown in the panel. */
    private static final int MAX_LOG = 5;

    // Keybind hints panel (a separate movable element).
    private static final String[] HINTS = {
            "O=Primary  U=Secondary", "P=Pause  J=Resume  K=Stop", "I=Paths  ;=Toggle HUD" };
    private static final int HINT_LINE_H = 10;
    private static final int HINT_PAD    = 4;

    /** Live transform of the hints panel — kept so it can auto-anchor before the user positions it. */
    private static HudTransform hintsTransform;

    private BotHudRenderer() {}

    // ── Shared HUD editor integration ──────────────────────────────────────────

    /**
     * Registers the HUD panel as a movable/scalable element with the shared editor. Call once at
     * mod init (after config load). The transform is backed by {@link BotConfig}; closing the editor
     * persists it. (The bottom-right keybind hint panel is intentionally not editable.)
     */
    public static void register() {
        BotConfig cfg = BotConfig.getInstance();

        HudTransform panel = new HudTransform(cfg.getHudX(), cfg.getHudY(), cfg.getHudScale());
        HudManager.register(HUD_OWNER, PANEL_ELEMENT, panel);

        hintsTransform = new HudTransform(cfg.getHintsHudX(), cfg.getHintsHudY(), cfg.getHintsHudScale());
        HudManager.register(HUD_OWNER, HINTS_ELEMENT, hintsTransform);

        HudTransform log = new HudTransform(cfg.getLogHudX(), cfg.getLogHudY(), cfg.getLogHudScale());
        HudManager.register(HUD_OWNER, LOG_ELEMENT, log);

        HudManager.onSave(HUD_OWNER, () -> {
            HudTransform pt = HudManager.transformOf(HUD_OWNER, PANEL_ELEMENT.id());
            if (pt != null) cfg.setHudLayout(pt.getX(), pt.getY(), pt.getScale());
            HudTransform ht = HudManager.transformOf(HUD_OWNER, HINTS_ELEMENT.id());
            if (ht != null) cfg.setHintsLayout(ht.getX(), ht.getY(), ht.getScale());
            HudTransform lt = HudManager.transformOf(HUD_OWNER, LOG_ELEMENT.id());
            if (lt != null) cfg.setLogLayout(lt.getX(), lt.getY(), lt.getScale());
        });
    }

    /** Opens the shared HUD editor scoped to Ceres's HUD. */
    public static void openEditor() {
        ensureHintsDefaultPositioned();
        HudManager.openEditor(HUD_OWNER);
    }

    /**
     * Until the user drags the hints panel, keep it anchored to the bottom-right corner (its old
     * fixed home). Recomputed each frame while unpositioned so it tracks window resizes; once the
     * user moves it in the editor, {@code hintsPositioned} sticks and this becomes a no-op.
     */
    private static void ensureHintsDefaultPositioned() {
        if (hintsTransform == null || BotConfig.getInstance().isHintsPositioned()) return;
        Minecraft client = Minecraft.getInstance();
        int hw = client.getWindow().getGuiScaledWidth();
        int hh = client.getWindow().getGuiScaledHeight();
        hintsTransform.moveTo(hw - hintPanelWidth() - 4, hh - hintPanelHeight() - 4);
    }

    /** The Ceres HUD panel as a single editor element (main panel + trailing log, coupled for now). */
    private static final HudElement PANEL_ELEMENT = new HudElement() {
        @Override public String id() { return "hud"; }
        @Override public String displayName() { return "Ceres HUD"; }
        @Override public boolean isEnabled() { return true; } // always editable; live draw is gated by GUI-visible
        @Override public int width()  { return PW; }
        @Override public int height() { return lastPanelHeight; }
        @Override public void render(GuiGraphicsExtractor ctx, boolean preview) { drawPanel(ctx); }
        @Override public void resetTransform(HudTransform t) { t.moveTo(4f, 4f); t.setScale(1f); } // default: top-left
    };

    /** The keybind-hints panel as its own movable element — hidden when the config toggle is off. */
    private static final HudElement HINTS_ELEMENT = new HudElement() {
        @Override public String id() { return "hints"; }
        @Override public String displayName() { return "Keybind Hints"; }
        @Override public boolean isEnabled() { return BotConfig.getInstance().isKeybindHintsVisible(); }
        @Override public int width()  { return hintPanelWidth(); }
        @Override public int height() { return hintPanelHeight(); }
        @Override public void render(GuiGraphicsExtractor ctx, boolean preview) { drawHints(ctx); }
        @Override public void resetTransform(HudTransform t) {
            // Default: bottom-right corner.
            Minecraft client = Minecraft.getInstance();
            int hw = client.getWindow().getGuiScaledWidth();
            int hh = client.getWindow().getGuiScaledHeight();
            t.moveTo(hw - hintPanelWidth() - 4, hh - hintPanelHeight() - 4);
            t.setScale(1f);
        }
    };

    /** The log panel as its own movable element — hidden when the config toggle is off. */
    private static final HudElement LOG_ELEMENT = new HudElement() {
        @Override public String id() { return "log"; }
        @Override public String displayName() { return "Ceres Log"; }
        @Override public boolean isEnabled() { return BotConfig.getInstance().isLogVisible(); }
        @Override public int width()  { return LOG_PW; }
        @Override public int height() { return lastLogHeight; }
        @Override public void render(GuiGraphicsExtractor ctx, boolean preview) { drawLog(ctx, preview); }
        @Override public void resetTransform(HudTransform t) { t.moveTo(4f, 150f); t.setScale(1f); }
    };

    // ── HUD render callback (registered with HudElementRegistry) ────────────────

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        BotStateManager state = BotStateManager.getInstance();
        if (!state.isGuiVisible()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        ensureHintsDefaultPositioned();
        HudManager.render(HUD_OWNER, ctx); // main panel + log, and hints (if enabled)
    }

    // ── Main panel + log (drawn from local origin) ──────────────────────────────

    private static void drawPanel(GuiGraphicsExtractor ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { drawSample(ctx); return; }

        BotStateManager state = BotStateManager.getInstance();
        Font tr         = client.font;
        LocalPlayer p   = client.player;
        BotState botState = state.getCurrentState();

        int stateCol = switch (botState) {
            case RUNNING -> 0xFF44EE44;
            case PAUSED  -> 0xFFFFAA00;
            case STOPPED -> 0xFFEE4444;
        };

        BotConfig cfg = BotConfig.getInstance();

        List<Waypoint> path = state.getCurrentPath();
        boolean running   = botState != BotState.STOPPED;
        boolean hasTarget = running && path != null && state.getCurrentPathIndex() < path.size();
        String plots         = state.getPlotsText();
        boolean showPlots    = !plots.isEmpty()    && cfg.isHudLineVisible("plots");
        String spray         = state.getSprayText();
        boolean showSpray    = !spray.isEmpty()    && cfg.isHudLineVisible("spray");
        String repellent     = state.getPestRepellentTimerText();
        boolean showRepel    = !repellent.isEmpty() && cfg.isHudLineVisible("repellent");
        String bonus         = state.getBonusText();
        boolean showBonus    = !bonus.isEmpty()    && cfg.isHudLineVisible("bonus");
        String cooldown      = state.getSprayCooldownText();
        boolean showCooldown = !cooldown.isEmpty() && cfg.isHudLineVisible("cooldown");
        String bpc           = state.getBonusPestChanceText();
        boolean showBpc      = !bpc.isEmpty()      && cfg.isHudLineVisible("pest_chance");

        int contentRows = 0;
        if (cfg.isHudLineVisible("profile"))  contentRows++;
        if (cfg.isHudLineVisible("area"))     contentRows++;
        if (cfg.isHudLineVisible("xyz"))      contentRows++;
        if (cfg.isHudLineVisible("look"))     contentRows++;
        if (cfg.isHudLineVisible("pests"))    contentRows++;
        if (showPlots)    contentRows++;
        if (showSpray)    contentRows++;
        if (showRepel)    contentRows++;
        if (showBonus)    contentRows++;
        if (showCooldown) contentRows++;
        if (showBpc)      contentRows++;
        if (running) {
            if (cfg.isHudLineVisible("path")) contentRows++;
            if (cfg.isHudLineVisible("bps"))  contentRows++;
        }
        if (hasTarget && cfg.isHudLineVisible("target")) contentRows++;

        int ph = HEADER + 1 + PAD + contentRows * LINE + PAD;
        lastPanelHeight = ph; // cache for the editor's outline/hit-box

        fill(ctx, PX,        PY,        PW,  ph, BG);
        fill(ctx, PX,        PY,        ACCENT, ph, stateCol);
        fill(ctx, PX+ACCENT, PY,        PW-ACCENT, HEADER, HEADER_TINT);
        fill(ctx, PX+ACCENT, PY+HEADER, PW-ACCENT, 1, DIVIDER);

        ctx.text(tr, "Ceres", PX + LX_OFF, PY + 3, 0xFFAAAAAA, false);
        String sn = botState.name();
        ctx.text(tr, sn, PX + PW - tr.width(sn) - 5, PY + 3, stateCol, false);

        int y = PY + HEADER + 1 + PAD;
        int lx = PX + LX_OFF;
        int vx = PX + VX_OFF;

        if (cfg.isHudLineVisible("profile")) {
            kv(ctx, tr, lx, vx, y, "Profile", state.getActiveProfileName(), VALUE_COL);
            y += LINE;
        }

        if (cfg.isHudLineVisible("area")) {
            String area = state.getCurrentArea();
            kv(ctx, tr, lx, vx, y, "Area", area.isEmpty() ? "Unknown" : area, VALUE_COL);
            y += LINE;
        }

        if (cfg.isHudLineVisible("xyz")) {
            String xyz = String.format("%.1f  /  %.1f  /  %.1f", p.getX(), p.getY(), p.getZ());
            kv(ctx, tr, lx, vx, y, "XYZ", xyz, VALUE_COL);
            y += LINE;
        }

        if (cfg.isHudLineVisible("look")) {
            String look = String.format("Y %.1f  P %.1f",
                    net.minecraft.util.Mth.wrapDegrees(p.getYRot()), p.getXRot());
            kv(ctx, tr, lx, vx, y, "Look", look, VALUE_COL);
            y += LINE;
        }

        if (cfg.isHudLineVisible("pests")) {
            int pests = state.getPestCount();
            int minP  = cfg.getMinPestCount();
            int pestCol = pests >= minP ? 0xFFEE4444 : 0xFF44EE44;
            kv(ctx, tr, lx, vx, y, "Pests", pests + " / " + minP, pestCol);
            y += LINE;
        }

        if (showPlots) {
            kv(ctx, tr, lx, vx, y, "Plots", plots, VALUE_COL);
            y += LINE;
        }

        if (showSpray) {
            int sprayCol = "None".equalsIgnoreCase(spray.trim()) ? 0xFFEE4444 : 0xFF44EE44;
            kv(ctx, tr, lx, vx, y, "Spray", spray.trim(), sprayCol);
            y += LINE;
        }

        if (showRepel) {
            int repCol = "None".equalsIgnoreCase(repellent.trim()) ? 0xFFEE4444 : 0xFF44EE44;
            kv(ctx, tr, lx, vx, y, "Repellent", repellent.trim(), repCol);
            y += LINE;
        }

        if (showBonus) {
            int bonusCol = "INACTIVE".equalsIgnoreCase(bonus.trim()) ? 0xFFEE4444 : 0xFF44EE44;
            kv(ctx, tr, lx, vx, y, "Bonus", bonus.trim(), bonusCol);
            y += LINE;
        }

        if (showCooldown) {
            int cdCol;
            if (cooldown.equalsIgnoreCase("READY")) {
                cdCol = 0xFF44EE44;                        // green — ready to spray
            } else if (cooldownSeconds(cooldown) <= 10) {
                cdCol = 0xFFFFAA00;                        // yellow — almost ready
            } else {
                cdCol = 0xFFEE4444;                        // red — long wait
            }
            kv(ctx, tr, lx, vx, y, "Cooldown", cooldown, cdCol);
            y += LINE;
        }

        if (showBpc) {
            int bpcCol = "DISABLED".equalsIgnoreCase(bpc) ? 0xFFEE4444 : 0xFF007700; // dark green when active
            kv(ctx, tr, lx, vx, y, "Pest Ch.", bpc, bpcCol);
            y += LINE;
        }

        if (running) {
            int idx = state.getCurrentPathIndex();

            if (cfg.isHudLineVisible("path")) {
                PathType pt = state.getCurrentPathType();
                int total   = path != null ? path.size() : 0;
                kv(ctx, tr, lx, vx, y, "Path", pt.name() + "  " + idx + " / " + total, 0xFFFFFFFF);
                y += LINE;
            }

            if (cfg.isHudLineVisible("bps")) {
                double bps    = state.getBlocksPerSecond();
                int    bpsCol = bps > 0 ? 0xFF44EE44 : 0xFF888888;
                kv(ctx, tr, lx, vx, y, "Bps", String.format("%.1f/s", bps), bpsCol);
                y += LINE;
            }

            if (hasTarget && cfg.isHudLineVisible("target")) {
                Waypoint tgt = path.get(idx);
                String ts = String.format("%.0f / %.0f / %.0f", tgt.x, tgt.y, tgt.z);
                kv(ctx, tr, lx, vx, y, "Target", ts, 0xFF888888);
            }
        }
    }

    /** The log panel, drawn from local origin (0,0) — now its own movable element. */
    private static void drawLog(GuiGraphicsExtractor ctx, boolean preview) {
        Font tr = Minecraft.getInstance().font;
        List<String> logs = BotLogger.getInstance().getRecentLines();

        // In the editor with no history, show sample lines so the element stays visible/positionable.
        List<String> sample = (preview && logs.isEmpty())
                ? List.of("[info] Started on PRIMARY", "[info] Reached waypoint 3", "[warn] Flag — soft stop")
                : null;
        List<String> src = sample != null ? sample : logs;
        if (src.isEmpty()) { lastLogHeight = 0; return; }

        int start = Math.max(0, src.size() - MAX_LOG);
        int shown = src.size() - start;
        int logH  = HEADER + 1 + shown * 9 + 4;
        lastLogHeight = logH;

        fill(ctx, 0,      0, LOG_PW,        logH,   LOG_BG);
        fill(ctx, 0,      0, ACCENT,        logH,   0x88888888);
        fill(ctx, ACCENT, 0, LOG_PW-ACCENT, HEADER, 0x10FFFFFF);
        fill(ctx, ACCENT, HEADER, LOG_PW-ACCENT, 1, 0x20FFFFFF);
        ctx.text(tr, "LOG", LX_OFF, 3, 0xFF555555, false);

        int ly = HEADER + 1 + 2;
        for (int i = start; i < src.size(); i++) {
            String line = src.get(i);
            int msgStart = line.indexOf("] ");
            if (msgStart >= 0) line = line.substring(msgStart + 2);
            if (line.length() > 52) line = line.substring(0, 52) + "…";
            ctx.text(tr, line, LX_OFF, ly, 0xFF999999, false);
            ly += 9;
        }
    }

    /** Minimal placeholder drawn when there is no player (editor opened from the main menu). */
    private static void drawSample(GuiGraphicsExtractor ctx) {
        Font tr = Minecraft.getInstance().font;
        int rows = 3;
        int ph = HEADER + 1 + PAD + rows * LINE + PAD;
        lastPanelHeight = ph;
        fill(ctx, PX, PY, PW, ph, BG);
        fill(ctx, PX, PY, ACCENT, ph, 0xFF44EE44);
        fill(ctx, PX+ACCENT, PY, PW-ACCENT, HEADER, HEADER_TINT);
        fill(ctx, PX+ACCENT, PY+HEADER, PW-ACCENT, 1, DIVIDER);
        ctx.text(tr, "Ceres", PX + LX_OFF, PY + 3, 0xFFAAAAAA, false);
        int y = PY + HEADER + 1 + PAD;
        kv(ctx, tr, PX + LX_OFF, PX + VX_OFF, y, "Profile", "Sample", VALUE_COL); y += LINE;
        kv(ctx, tr, PX + LX_OFF, PX + VX_OFF, y, "Area", "Garden", VALUE_COL);    y += LINE;
        kv(ctx, tr, PX + LX_OFF, PX + VX_OFF, y, "Pests", "0 / 4", 0xFF44EE44);
    }

    // ── Keybind hints element (drawn from local origin) ──────────────────────────

    private static int hintPanelWidth() {
        Font tr = Minecraft.getInstance().font;
        int w = 0;
        for (String h : HINTS) w = Math.max(w, tr.width(h));
        return w + HINT_PAD * 2;
    }

    private static int hintPanelHeight() {
        return HINTS.length * HINT_LINE_H + HINT_PAD * 2 - 1;
    }

    private static void drawHints(GuiGraphicsExtractor ctx) {
        Font tr = Minecraft.getInstance().font;
        int w = hintPanelWidth();
        int h = hintPanelHeight();
        fill(ctx, 0, 0, w, h, 0x90000000);
        fill(ctx, 0, 0, ACCENT, h, 0x88666666);
        int hy = HINT_PAD;
        for (String hint : HINTS) {
            ctx.text(tr, hint, HINT_PAD + ACCENT, hy, HINT_COL, false);
            hy += HINT_LINE_H;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static void fill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    private static void kv(GuiGraphicsExtractor ctx, Font tr,
                            int lx, int vx, int y,
                            String label, String value, int valueCol) {
        ctx.text(tr, label, lx, y, LABEL_COL, false);
        ctx.text(tr, value, vx, y, valueCol, false);
    }

    /**
     * Parses a cooldown string like "2m 36s" or "36s" into total seconds.
     * Returns 0 for unrecognised formats.
     */
    private static int cooldownSeconds(String text) {
        int total = 0;
        for (String part : text.trim().split("\\s+")) {
            try {
                if (part.endsWith("m"))
                    total += Integer.parseInt(part.substring(0, part.length() - 1)) * 60;
                else if (part.endsWith("s"))
                    total += Integer.parseInt(part.substring(0, part.length() - 1));
            } catch (NumberFormatException ignored) {}
        }
        return total;
    }
}

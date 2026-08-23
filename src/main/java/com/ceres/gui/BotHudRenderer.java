package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.ceres.path.PathType;
import com.ceres.path.Waypoint;
import com.playerapi.config.theme.ConfigStyle;
import com.playerapi.config.theme.Surface;
import com.playerapi.config.theme.ThemeRenderer;
import com.playerapi.hud.HudElement;
import com.playerapi.hud.HudManager;
import com.playerapi.hud.HudTransform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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

    // Textured styles use a LIGHT stone panel, so text is dark-on-light (matches the reference kit).
    private static final int D_LABEL = 0xFF5A4A38; // dark warm-brown labels
    private static final int D_VALUE = 0xFF2A2418; // near-black values
    private static final int D_TITLE = 0xFF3A2A16; // dark title on the stone plaque
    // Status colours remapped to read on light stone.
    private static final int D_GREEN = 0xFF2E8B3A;
    private static final int D_RED   = 0xFFB63A2C;
    private static final int D_AMBER = 0xFFB77400;

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

    // ── HUD style (Custom / Toned Down / Flat) — drives how panels are drawn ─────
    /** Chunky bright farm panel (Custom) + a muted transparent one (Toned Down); header plaque. */
    private static final Surface PANEL       = Surface.nineSlice(hudTex("hud_panel"), 64, 11);
    private static final Surface PANEL_TONED = Surface.nineSlice(hudTex("hud_panel_toned"), 64, 11);
    private static final Surface HEADER_BAND = Surface.nineSlice(hudTex("hud_header"), 32, 8);
    /** Decorative corner props (Custom only) — "mini notes thrown around to complete the look." */
    private static final Identifier PROP_LEAVES = hudTex("prop_leaves");
    private static final Identifier PROP_WHEAT  = hudTex("prop_wheat");
    private static final Identifier PROP_CARROT = hudTex("prop_carrot");
    private static final Identifier PROP_FLOWER = hudTex("prop_flower");
    private static final int PROP = 16;   // drawn prop size (smaller so it tucks into the corner)
    private static final int PROP_TEX = 32; // prop texture size
    private static final int PROP_OH = 4;   // corner overhang

    private static Identifier hudTex(String name) {
        return Identifier.fromNamespaceAndPath("playerapi", "textures/config/ceres/" + name + ".png");
    }

    private static ConfigStyle style() {
        return BotConfig.getInstance().getHudStyle();
    }

    /** Draw a panel background in the current HUD style: textured (Custom/Toned) or a flat colour fill. */
    private static void drawBg(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int flatColor) {
        switch (style()) {
            case CUSTOM     -> ThemeRenderer.surface(ctx, PANEL, flatColor, x, y, w, h);
            case TONED_DOWN -> ThemeRenderer.surface(ctx, PANEL_TONED, flatColor, x, y, w, h);
            case FLAT       -> fill(ctx, x, y, w, h, flatColor);
        }
    }

    /** Inset for the accent stripe so it sits inside the textured panel's rounded frame. */
    private static int accentInset() {
        return style() == ConfigStyle.FLAT ? 0 : 4;
    }

    /** Draw the left accent stripe, inset within the frame. Skipped in Custom (state shows in the header). */
    private static void drawAccent(GuiGraphicsExtractor ctx, int x, int y, int h, int color) {
        if (isCustom()) return;
        int ai = accentInset();
        fill(ctx, x + ai, y + ai, ACCENT, h - 2 * ai, color);
    }

    /** Frame inset: content is pushed in by this many px on textured panels so text clears the frame bevel. */
    private static int fi() { return isTextured() ? 6 : 0; }

    /** Draw the header band (height {@code h}): a stone plaque (Custom) or the flat tint + divider. */
    private static void drawHeader(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int tint, int divider) {
        int f = fi();
        int hx = x + ACCENT + f, hw = w - ACCENT - ACCENT - f * 2;
        if (style() == ConfigStyle.CUSTOM) {
            ThemeRenderer.surface(ctx, HEADER_BAND, tint, hx, y + f, hw, h - f);
        } else {
            fill(ctx, hx, y, hw, HEADER, tint);
            fill(ctx, hx, y + HEADER, hw, 1, divider);
        }
    }

    /** Blit a square sprite (respects PNG alpha). */
    private static void sprite(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int size) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, PROP_TEX, PROP_TEX, PROP_TEX, PROP_TEX);
    }

    /** Draw the four decorative corner props overhanging a panel of size {@code w×h} (Custom only). */
    private static void drawProps(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        if (style() != ConfigStyle.CUSTOM) return;
        int d = PROP, o = PROP_OH;
        sprite(ctx, PROP_LEAVES, x - o,             y - o,             d);
        sprite(ctx, PROP_WHEAT,  x + w - d + o,      y - o,             d);
        sprite(ctx, PROP_CARROT, x - o,             y + h - d + o,      d);
        sprite(ctx, PROP_FLOWER, x + w - d + o,      y + h - d + o,      d);
    }

    // ── Style-aware text ─────────────────────────────────────────────────────────
    /** Custom + Toned both render on the light stone panel → dark-on-light text. Flat keeps the classic look. */
    private static boolean isTextured()   { return style() == ConfigStyle.CUSTOM || style() == ConfigStyle.TONED_DOWN; }
    private static boolean isCustom()      { return style() == ConfigStyle.CUSTOM; }
    private static boolean shadow()        { return false; } // dark text on light stone needs no shadow
    private static int     labelCol()      { return isTextured() ? D_LABEL : LABEL_COL; }
    private static int     titleCol()      { return isTextured() ? D_TITLE : 0xFFAAAAAA; }

    /** Remap a per-row value colour to a light-stone-readable one for textured styles; pass through on Flat. */
    private static int mapVal(int c) {
        if (!isTextured()) return c;
        return switch (c) {
            case VALUE_COL, 0xFFFFFFFF -> D_VALUE;   // neutral/white → near-black
            case 0xFF44EE44            -> D_GREEN;   // status green
            case 0xFFEE4444            -> D_RED;     // status red
            case 0xFFFFAA00            -> D_AMBER;   // status amber
            case 0xFF007700            -> D_GREEN;   // dark green (active) → readable green
            case 0xFF888888            -> 0xFF6A6055; // grey → warm grey
            default                    -> c;
        };
    }

    /** Bot-state colour, remapped to read on the light stone panel for textured styles. */
    private static int stateColour(BotState s) {
        if (isTextured()) return switch (s) { case RUNNING -> D_GREEN; case PAUSED -> D_AMBER; case STOPPED -> D_RED; };
        return switch (s) { case RUNNING -> 0xFF44EE44; case PAUSED -> 0xFFFFAA00; case STOPPED -> 0xFFEE4444; };
    }

    /**
     * State indicator top-right: on the stone panel a small rounded status <b>pill</b> (colour = state)
     * with white text — a clean badge; on Flat the classic coloured word. {@code rightX} = right anchor.
     */
    private static void drawState(GuiGraphicsExtractor ctx, Font tr, int rightX, int y, String label, int stateCol) {
        int tw = tr.width(label);
        if (isTextured()) {
            int padX = 4, h = tr.lineHeight + 1, w = tw + padX * 2, x = rightX - w, by = y - 1;
            fill(ctx, x + 1, by, w - 2, h, stateCol);          // pill body
            fill(ctx, x, by + 1, 1, h - 2, stateCol);          // fake-rounded left edge
            fill(ctx, x + w - 1, by + 1, 1, h - 2, stateCol);  // right edge
            ctx.text(tr, label, x + padX, y, 0xFFFFFFFF, false);
        } else {
            ctx.text(tr, label, rightX - tw, y, stateCol, false);
        }
    }

    /** Last computed main-panel height, cached for the editor's outline/hit-box. */
    private static int lastPanelHeight = HEADER + 1 + PAD + 5 * LINE + PAD;
    /** Last computed log-panel height, cached for the editor's outline/hit-box. */
    private static int lastLogHeight = HEADER + 1 + 5 * 9 + 4;
    /** Max log lines shown in the panel. */
    private static final int MAX_LOG = 5;

    private BotHudRenderer() {}

    // ── Shared HUD editor integration ──────────────────────────────────────────

    /**
     * Registers the HUD panel as a movable/scalable element with the shared editor. Call once at
     * mod init (after config load). The transform is backed by {@link BotConfig}; closing the editor
     * persists it.
     */
    public static void register() {
        BotConfig cfg = BotConfig.getInstance();

        HudTransform panel = new HudTransform(cfg.getHudX(), cfg.getHudY(), cfg.getHudScale());
        HudManager.register(HUD_OWNER, PANEL_ELEMENT, panel);

        HudTransform log = new HudTransform(cfg.getLogHudX(), cfg.getLogHudY(), cfg.getLogHudScale());
        HudManager.register(HUD_OWNER, LOG_ELEMENT, log);

        HudManager.onSave(HUD_OWNER, () -> {
            HudTransform pt = HudManager.transformOf(HUD_OWNER, PANEL_ELEMENT.id());
            if (pt != null) cfg.setHudLayout(pt.getX(), pt.getY(), pt.getScale());
            HudTransform lt = HudManager.transformOf(HUD_OWNER, LOG_ELEMENT.id());
            if (lt != null) cfg.setLogLayout(lt.getX(), lt.getY(), lt.getScale());
        });
    }

    /** Opens the shared HUD editor scoped to Ceres's HUD. */
    public static void openEditor() {
        HudManager.openEditor(HUD_OWNER);
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

        HudManager.render(HUD_OWNER, ctx); // main panel + log
    }

    // ── Main panel + log (drawn from local origin) ──────────────────────────────

    private static void drawPanel(GuiGraphicsExtractor ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { drawSample(ctx); return; }

        BotStateManager state = BotStateManager.getInstance();
        Font tr         = client.font;
        LocalPlayer p   = client.player;
        BotState botState = state.getCurrentState();

        int stateCol = stateColour(botState);

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

        int fi = fi();
        int hdr = HEADER + fi;                          // taller header on textured (room below the frame)
        int prop = isCustom() ? 6 : 0;                  // extra nudge so header text clears the corner props
        int ph = hdr + 1 + PAD + contentRows * LINE + PAD + fi;
        lastPanelHeight = ph; // cache for the editor's outline/hit-box

        drawBg(ctx, PX, PY, PW, ph, BG);
        drawAccent(ctx, PX, PY, ph, stateCol);
        drawHeader(ctx, PX, PY, PW, hdr, HEADER_TINT, DIVIDER);

        ctx.text(tr, "Ceres", PX + LX_OFF + fi + prop + 5, PY + fi + 3, titleCol(), shadow());
        drawState(ctx, tr, PX + PW - 5 - fi - prop, PY + fi + 3, botState.name(), stateCol);

        int y = PY + hdr + 1 + PAD;
        int lx = PX + LX_OFF + fi;
        int vx = PX + VX_OFF + fi;

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

        drawProps(ctx, PX, PY, PW, ph); // decorative corner props (Custom style only)
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
        int fi = fi();
        int hdr = HEADER + fi;
        int logH  = hdr + 1 + shown * 9 + 4 + fi;
        lastLogHeight = logH;

        drawBg(ctx, 0, 0, LOG_PW, logH, LOG_BG);
        drawAccent(ctx, 0, 0, logH, 0x88888888);
        drawHeader(ctx, 0, 0, LOG_PW, hdr, 0x10FFFFFF, 0x20FFFFFF);
        ctx.text(tr, "LOG", LX_OFF + fi + 18, fi + 3, isTextured() ? D_TITLE : 0xFF555555, shadow());

        int ly = hdr + 1 + 2;
        for (int i = start; i < src.size(); i++) {
            String line = src.get(i);
            int msgStart = line.indexOf("] ");
            if (msgStart >= 0) line = line.substring(msgStart + 2);
            if (line.length() > 52) line = line.substring(0, 52) + "…";
            ctx.text(tr, line, LX_OFF + fi, ly, isTextured() ? 0xFF4A423A : 0xFF999999, shadow());
            ly += 9;
        }
    }

    /** Minimal placeholder drawn when there is no player (editor opened from the main menu). */
    private static void drawSample(GuiGraphicsExtractor ctx) {
        Font tr = Minecraft.getInstance().font;
        int rows = 3;
        int fi = fi();
        int hdr = HEADER + fi;
        int prop = isCustom() ? 6 : 0;
        int ph = hdr + 1 + PAD + rows * LINE + PAD + fi;
        lastPanelHeight = ph;
        drawBg(ctx, PX, PY, PW, ph, BG);
        drawAccent(ctx, PX, PY, ph, stateColour(BotState.RUNNING));
        drawHeader(ctx, PX, PY, PW, hdr, HEADER_TINT, DIVIDER);
        ctx.text(tr, "Ceres", PX + LX_OFF + fi + prop, PY + fi + 3, titleCol(), shadow());
        int y = PY + hdr + 1 + PAD;
        int lx = PX + LX_OFF + fi, vx = PX + VX_OFF + fi;
        kv(ctx, tr, lx, vx, y, "Profile", "Sample", VALUE_COL); y += LINE;
        kv(ctx, tr, lx, vx, y, "Area", "Garden", VALUE_COL);    y += LINE;
        kv(ctx, tr, lx, vx, y, "Pests", "0 / 4", 0xFF44EE44);
        drawProps(ctx, PX, PY, PW, ph);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static void fill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    private static void kv(GuiGraphicsExtractor ctx, Font tr,
                            int lx, int vx, int y,
                            String label, String value, int valueCol) {
        ctx.text(tr, label, lx, y, labelCol(), shadow());
        ctx.text(tr, value, vx, y, mapVal(valueCol), shadow());
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

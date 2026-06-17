package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.ceres.path.PathType;
import com.ceres.path.Waypoint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

public class BotHudRenderer {

    private static final int BG          = 0xC0000000;
    private static final int HEADER_TINT = 0x18FFFFFF;
    private static final int DIVIDER     = 0x30FFFFFF;
    private static final int LABEL_COL   = 0xFF666666;
    private static final int VALUE_COL   = 0xFFCCCCCC;
    private static final int LOG_BG      = 0x90000000;
    private static final int HINT_COL    = 0x88888888;

    private static final int PX      = 4;
    private static final int PY      = 4;
    private static final int PW      = 210;
    private static final int LOG_PW  = 310;
    private static final int ACCENT  = 3;
    private static final int HEADER  = 13;
    private static final int PAD     = 5;
    private static final int LINE    = 11;
    private static final int LX_OFF  = 7;
    private static final int VX_OFF  = 60;

    private BotHudRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        BotStateManager state = BotStateManager.getInstance();
        if (!state.isGuiVisible()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

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

        List<String> logs = BotLogger.getInstance().getRecentLines();
        if (!logs.isEmpty()) {
            int maxLog  = 5;
            int start   = Math.max(0, logs.size() - maxLog);
            int shown   = logs.size() - start;
            int logH    = HEADER + 1 + shown * 9 + 4;
            int logY    = PY + ph + 3;

            fill(ctx, PX,        logY, LOG_PW,    logH, LOG_BG);
            fill(ctx, PX,        logY, ACCENT, logH, 0x88888888);
            fill(ctx, PX+ACCENT, logY, LOG_PW-ACCENT, HEADER, 0x10FFFFFF);
            fill(ctx, PX+ACCENT, logY+HEADER, LOG_PW-ACCENT, 1, 0x20FFFFFF);
            ctx.text(tr, "LOG", PX + LX_OFF, logY + 3, 0xFF555555, false);

            int ly = logY + HEADER + 1 + 2;
            for (int i = start; i < logs.size(); i++) {
                String line = logs.get(i);
                int msgStart = line.indexOf("] ");
                if (msgStart >= 0) line = line.substring(msgStart + 2);
                if (line.length() > 52) line = line.substring(0, 52) + "…";
                ctx.text(tr, line, PX + LX_OFF, ly, 0xFF999999, false);
                ly += 9;
            }
        }

        int hw = client.getWindow().getGuiScaledWidth();
        int hh = client.getWindow().getGuiScaledHeight();
        String[] hints = { "O=Primary  U=Secondary", "P=Pause  J=Resume  K=Stop", "I=Paths  ;=Toggle HUD" };
        int hintLineH = 10;
        int hintPad   = 4;
        int hintW     = 0;
        for (String h : hints) hintW = Math.max(hintW, tr.width(h));
        int hintPanelW = hintW + hintPad * 2;
        int hintPanelH = hints.length * hintLineH + hintPad * 2 - 1;
        int hbx = hw - hintPanelW - 4;
        int hby = hh - hintPanelH - 4;
        fill(ctx, hbx, hby, hintPanelW, hintPanelH, 0x90000000);
        fill(ctx, hbx, hby, ACCENT, hintPanelH, 0x88666666);
        int hy = hby + hintPad;
        for (String h : hints) {
            ctx.text(tr, h, hbx + hintPad + ACCENT, hy, HINT_COL, false);
            hy += hintLineH;
        }
    }

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

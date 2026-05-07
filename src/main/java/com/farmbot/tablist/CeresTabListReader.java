package com.ceres.tablist;

import com.ceres.core.BotStateManager;
import com.playerapi.TabListInfo;

/**
 * Ceres tab-list parser.
 * Reads Area, Alive pest count, and Repellent timer from the tab list
 * using the generic TabListInfo API from PlayerAPI.
 */
public final class CeresTabListReader {

    private static final String PREFIX_AREA       = "Area:";
    private static final String PREFIX_ALIVE      = "Alive:";
    private static final String PREFIX_PLOTS      = "Plots:";
    private static final String PREFIX_SPRAY      = "Spray:";
    private static final String PREFIX_REPELLENT  = "Repellent:";
    private static final String PREFIX_BONUS      = "Bonus:";
    private static final String PREFIX_COOLDOWN        = "Cooldown:";
    private static final String PREFIX_BONUS_PEST_CH   = "Bonus Pest Chance:";

    private CeresTabListReader() {}

    /** Parse the tab list and push values into BotStateManager. Safe to call every tick. */
    public static void update() {
        BotStateManager state = BotStateManager.getInstance();

        String areaLine = TabListInfo.findLineContaining(PREFIX_AREA);
        if (areaLine != null) {
            int idx = areaLine.indexOf(PREFIX_AREA);
            state.setCurrentArea(areaLine.substring(idx + PREFIX_AREA.length()).trim());
        }

        String aliveLine = TabListInfo.findLineContaining(PREFIX_ALIVE);
        if (aliveLine != null) {
            int idx = aliveLine.indexOf(PREFIX_ALIVE);
            String raw = aliveLine.substring(idx + PREFIX_ALIVE.length()).trim();
            try {
                int spaceIdx = raw.indexOf(' ');
                String numStr = spaceIdx > 0 ? raw.substring(0, spaceIdx) : raw;
                state.setPestCount(Integer.parseInt(numStr));
            } catch (NumberFormatException ignored) {}
        }

        String plotsLine = TabListInfo.findLineContaining(PREFIX_PLOTS);
        if (plotsLine != null) {
            int idx = plotsLine.indexOf(PREFIX_PLOTS);
            state.setPlotsText(plotsLine.substring(idx + PREFIX_PLOTS.length()).trim());
        } else {
            state.setPlotsText("");
        }

        String sprayLine = TabListInfo.findLineContaining(PREFIX_SPRAY);
        if (sprayLine != null) {
            int idx = sprayLine.indexOf(PREFIX_SPRAY);
            state.setSprayText(sprayLine.substring(idx + PREFIX_SPRAY.length()).trim());
        } else {
            state.setSprayText("");
        }

        String repellentLine = TabListInfo.findLineContaining(PREFIX_REPELLENT);
        if (repellentLine != null) {
            int idx = repellentLine.indexOf(PREFIX_REPELLENT);
            state.setPestRepellentTimerText(repellentLine.substring(idx + PREFIX_REPELLENT.length()).trim());
        } else {
            state.setPestRepellentTimerText("");
        }

        String bonusLine = TabListInfo.findLineContaining(PREFIX_BONUS);
        if (bonusLine != null) {
            int idx = bonusLine.indexOf(PREFIX_BONUS);
            state.setBonusText(bonusLine.substring(idx + PREFIX_BONUS.length()).trim());
        } else {
            state.setBonusText("");
        }

        String cooldownLine = TabListInfo.findLineContaining(PREFIX_COOLDOWN);
        if (cooldownLine != null) {
            int idx = cooldownLine.indexOf(PREFIX_COOLDOWN);
            state.setSprayCooldownText(cooldownLine.substring(idx + PREFIX_COOLDOWN.length()).trim());
        } else {
            state.setSprayCooldownText("");
        }

        String bpcLine = TabListInfo.findLineContaining(PREFIX_BONUS_PEST_CH);
        if (bpcLine != null) {
            if (TabListInfo.isLineStrikethrough(PREFIX_BONUS_PEST_CH)) {
                state.setBonusPestChanceText("DISABLED");
            } else {
                int idx = bpcLine.indexOf(PREFIX_BONUS_PEST_CH);
                state.setBonusPestChanceText(bpcLine.substring(idx + PREFIX_BONUS_PEST_CH.length()).trim());
            }
        } else {
            state.setBonusPestChanceText("");
        }
    }
}

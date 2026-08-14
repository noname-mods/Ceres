package com.ceres.checkers;

import com.ceres.core.BotStateManager;

/**
 * Detects whether the held tool changed from the one the bot started with, compared by the stable
 * <b>SkyBlock internal id</b> (not the display name, which Hypixel renames). An empty initial id (e.g.
 * a non-SkyBlock item) is treated as "capture current" on first check.
 */
public class ToolChecker {

    private String initialToolId = null;

    public void reset() {
        initialToolId = BotStateManager.getInstance().getInitialToolId();
    }

    /** @param currentToolId the currently held item's SkyBlock id (may be empty). */
    public boolean check(String currentToolId) {
        if (initialToolId == null || initialToolId.isEmpty()) {
            initialToolId = currentToolId;
            return true;
        }
        return initialToolId.equals(currentToolId);
    }
}

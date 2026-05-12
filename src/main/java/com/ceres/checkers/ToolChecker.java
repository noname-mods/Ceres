package com.ceres.checkers;

import com.ceres.core.BotStateManager;

public class ToolChecker {

    private String initialTool = null;

    public void reset() {
        initialTool = BotStateManager.getInstance().getInitialTool();
    }

    public boolean check(String currentTool) {
        if (initialTool == null || initialTool.isEmpty()) {
            initialTool = currentTool;
            return true;
        }
        return initialTool.equalsIgnoreCase(currentTool);
    }
}

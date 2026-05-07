package com.ceres.checkers;

import com.ceres.core.BotConfig;
import com.ceres.core.BotStateManager;

public class PestChecker {

    private long lastAlertTick = -600;

    public void reset() {}

    public boolean check(long currentTick) {
        int pestCount = BotStateManager.getInstance().getPestCount();
        int minPest   = BotConfig.getInstance().getMinPestCount();

        if (pestCount >= minPest && currentTick - lastAlertTick > 600) {
            lastAlertTick = currentTick;
            return false;
        }
        return true;
    }
}

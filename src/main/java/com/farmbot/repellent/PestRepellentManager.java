package com.ceres.repellent;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.playerapi.InventoryActions;
import com.playerapi.InteractionActions;
import com.playerapi.Scheduler;

public class PestRepellentManager {

    private static final PestRepellentManager INSTANCE = new PestRepellentManager();

    private static final String REPELLENT_ITEM = "Pest Repellent";
    private static final int REAPPLY_COOLDOWN_TICKS = 1200;

    private boolean isApplying = false;
    private long lastApplyTick = -REAPPLY_COOLDOWN_TICKS;

    private PestRepellentManager() {}

    public static PestRepellentManager getInstance() {
        return INSTANCE;
    }

    public void tick(long currentTick) {
        if (!BotConfig.getInstance().isRepellentReapplyEnabled()) return;
        if (isApplying) return;

        BotStateManager state = BotStateManager.getInstance();
        if (state.getCurrentState() == BotState.STOPPED) return;

        String timer = state.getPestRepellentTimerText();
        if ("None".equalsIgnoreCase(timer.trim())
                && currentTick - lastApplyTick >= REAPPLY_COOLDOWN_TICKS) {
            applyRepellent(currentTick, state);
        }
    }

    private void applyRepellent(long currentTick, BotStateManager state) {
        isApplying = true;
        lastApplyTick = currentTick;
        BotLogger.getInstance().logInfo("PestRepellentManager: Repellent expired, applying new one");

        BotState prevState = state.getCurrentState();
        if (prevState == BotState.RUNNING) state.pauseBot();

        if (!InventoryActions.switchToItemStartingWith(REPELLENT_ITEM)) {
            BotLogger.getInstance().logWarn("PestRepellentManager: no item starting with '"
                    + REPELLENT_ITEM + "' found in hotbar — skipping reapply");
            isApplying = false;
            if (prevState == BotState.RUNNING) state.resumeBot();
            return;
        }

        Scheduler.schedule(5, () -> {
            InteractionActions.useItem();
            BotLogger.getInstance().logInfo("PestRepellentManager: Repellent applied, waiting 60s");

            Scheduler.schedule(REAPPLY_COOLDOWN_TICKS, () -> {
                isApplying = false;
                if (prevState == BotState.RUNNING && state.getCurrentState() == BotState.PAUSED) {
                    state.resumeBot();
                    BotLogger.getInstance().logInfo("PestRepellentManager: Resumed after repellent cooldown");
                }
            });
        });
    }
}

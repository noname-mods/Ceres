package com.ceres.repellent;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import com.playerapi.InventoryActions;
import com.playerapi.InventoryInfo;
import com.playerapi.InteractionActions;
import com.playerapi.Scheduler;

/**
 * Auto-reapplies Pest Repellent when the tab-list timer reads "None". The reapply is a brief,
 * human-paced interruption: stop moving, switch to the repellent, right-click it, switch <b>back to the
 * farm tool that was held</b>, then resume farming. It no longer parks the bot for a full minute — the
 * 60s figure is only the re-trigger cooldown, not how long we stay stopped.
 */
public class PestRepellentManager {

    private static final PestRepellentManager INSTANCE = new PestRepellentManager();

    private static final String REPELLENT_ITEM = "Pest Repellent";
    /** Minimum ticks between reapplies (~60s) — guards the brief window before the tab timer updates. */
    private static final int REAPPLY_COOLDOWN_TICKS = 1200;

    private boolean isApplying = false;
    private long lastApplyTick = -REAPPLY_COOLDOWN_TICKS;

    private PestRepellentManager() {}

    public static PestRepellentManager getInstance() {
        return INSTANCE;
    }

    public void tick(long currentTick) {
        if (!BotConfig.getInstance().isRepellentReapplyEnabled()) return;

        BotStateManager state = BotStateManager.getInstance();
        if (state.getCurrentState() == BotState.STOPPED) {
            // Bot isn't running — drop any half-finished apply so we start clean next run
            // (guards against isApplying getting stuck if the bot was stopped mid-reapply).
            isApplying = false;
            return;
        }
        if (isApplying) return;

        // Only reapply while actively farming — not while the user has it paused.
        if (state.getCurrentState() != BotState.RUNNING) return;

        String timer = state.getPestRepellentTimerText();
        if ("None".equalsIgnoreCase(timer.trim())
                && currentTick - lastApplyTick >= REAPPLY_COOLDOWN_TICKS) {
            applyRepellent(currentTick, state);
        }
    }

    private void applyRepellent(long currentTick, BotStateManager state) {
        isApplying = true;
        lastApplyTick = currentTick;

        // Remember the farm tool's slot so we can return to it after applying.
        final int originalSlot = InventoryInfo.getSelectedSlot();

        BotLogger.getInstance().logInfo("PestRepellentManager: Repellent expired — reapplying");

        // Stop moving/attacking during the swap so we don't farm with the repellent held.
        state.pauseBot();

        if (!InventoryActions.switchToItemStartingWith(REPELLENT_ITEM)) {
            BotLogger.getInstance().logWarn("PestRepellentManager: no item starting with '"
                    + REPELLENT_ITEM + "' found in hotbar — skipping reapply");
            state.resumeBot();
            isApplying = false;
            return;
        }

        // Human-paced: settle on the item, right-click it, switch back to the tool, resume farming.
        int useDelay = 6 + (int) (Math.random() * 6);   // ~0.3–0.6s to "reach" the item
        Scheduler.schedule(useDelay, () -> {
            InteractionActions.useItem();
            BotLogger.getInstance().logInfo("PestRepellentManager: Repellent applied");

            int backDelay = 6 + (int) (Math.random() * 6); // ~0.3–0.6s before swapping back
            Scheduler.schedule(backDelay, () -> {
                InventoryActions.switchToSlot(originalSlot); // back to the farm tool
                if (state.getCurrentState() == BotState.PAUSED) {
                    state.resumeBot();
                    BotLogger.getInstance().logInfo("PestRepellentManager: Back to farming");
                }
                isApplying = false;
            });
        });
    }
}

package com.ceres.checkers;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotStateManager;
import com.playerapi.DisplayActions;
import com.playerapi.PlayerInfo;

public class CheckerController {

    private static final CheckerController INSTANCE = new CheckerController();

    private final InventoryChecker inventoryChecker = new InventoryChecker();
    private final ToolChecker      toolChecker      = new ToolChecker();
    private final YawPitchChecker  yawPitchChecker  = new YawPitchChecker();
    private final PestChecker      pestChecker      = new PestChecker();

    private long nextResetTick = 0;

    private CheckerController() {}

    public static CheckerController getInstance() {
        return INSTANCE;
    }

    public void tick(long currentTick) {
        if (BotStateManager.getInstance().isChecksBypassed()) return;
        if (currentTick >= nextResetTick) {
            resetAll(currentTick);
            nextResetTick = currentTick + 60 + (long)(Math.random() * 40);
            return;
        }
        runChecks(currentTick);
    }

    private void resetAll(long currentTick) {
        if (!PlayerInfo.isInWorld()) return;
        inventoryChecker.reset(currentTick);
        toolChecker.reset();
        yawPitchChecker.reset(PlayerInfo.getYaw(), PlayerInfo.getPitch());
        pestChecker.reset();
    }

    private void runChecks(long currentTick) {
        if (!PlayerInfo.isInWorld()) return;

        BotConfig cfg = BotConfig.getInstance();
        BotStateManager state = BotStateManager.getInstance();
        String heldItem = PlayerInfo.getHeldItem().displayName();

        // ── Area check: leaving the Garden is the only hard stop ─────────────
        // Other mods can teleport the player out of the Garden at any time.
        // Only fire when the area is positively known (non-empty) and not "Garden"
        // to avoid false positives while the tab list is loading.
        String area = state.getCurrentArea();
        if (!area.isEmpty() && !area.equalsIgnoreCase("Garden")) {
            BotLogger.getInstance().logWarn(
                    "Left Garden (now in: " + area + ") — stopping bot");
            state.emergencyStop();
            cfg.getStopAlertSound().play();
            return;
        }

        // ── Inventory checker ─────────────────────────────────────────────────
        if (cfg.isInventoryCheckerEnabled() && !inventoryChecker.check(currentTick)) {
            BotLogger.getInstance().logWarn("InventoryChecker: No inventory changes for 6s — stopping");
            state.stopBot();
            cfg.getStopAlertSound().play();
            return;
        }

        // ── Tool checker (warn only — other mods legitimately switch items) ───
        if (cfg.isToolCheckerEnabled() && !toolChecker.check(heldItem)) {
            BotLogger.getInstance().logWarn("ToolChecker: Held item changed from '"
                    + state.getInitialTool() + "' to '" + heldItem + "' — continuing");
            // Do not stop: rotation checkers and other mods routinely switch slots.
            // The bot continues moving and attacking on the current path.
        }

        // ── Yaw/pitch checker — HIGHEST priority alert, bot keeps farming ────
        // Unexpected rotation is the most suspicious signal: it may indicate
        // a player or mod is controlling the camera. Show a loud title and
        // play the warn sound, but do NOT stop — the path continues uninterrupted.
        if (cfg.isYawPitchCheckerEnabled()
                && !yawPitchChecker.check(PlayerInfo.getYaw(), PlayerInfo.getPitch())) {
            BotLogger.getInstance().logWarn("YawPitchChecker: Rotation changed unexpectedly — alerting");
            DisplayActions.showTitle("§c⚠ LOOK CHANGED", "§eBot is still running", 3, 40, 10);
            cfg.getWarnAlertSound().play();
        }

        // ── Pest checker (warn only) ──────────────────────────────────────────
        if (cfg.isPestCheckerEnabled() && !pestChecker.check(currentTick)) {
            BotLogger.getInstance().logWarn("PestChecker: Pest count "
                    + state.getPestCount() + " >= threshold");
            cfg.getWarnAlertSound().play();
        }
    }

    public void forceReset(long currentTick) {
        resetAll(currentTick);
        nextResetTick = currentTick + 60 + (long)(Math.random() * 40);
    }
}

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

    /**
     * Grace period after the bot starts before checks actually run (~2s). The cycle-start command
     * ({@code /warp garden}) repositions AND reorients the player a moment after start, so if you began
     * facing a different way the yaw/pitch (and no-movement) checks would flag that warp-induced change.
     * During the grace we only keep re-baselining, so the baseline settles on the post-warp state.
     */
    private static final int STARTUP_GRACE_TICKS = 40;

    // ── No-movement detection (player stalled while the bot should be moving) ────
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private int    stallTicks = 0;
    /** Ticks of no position change before the no-movement flag fires (~2s). */
    private static final int NO_MOVE_TICKS = 40;

    private CheckerController() {}

    public static CheckerController getInstance() {
        return INSTANCE;
    }

    public void tick(long currentTick) {
        if (BotStateManager.getInstance().isChecksBypassed()) return;

        // Startup grace: keep re-baselining (yaw/pitch, position, tool, inventory) but don't check yet,
        // so /warp garden's reposition + reorientation doesn't trip a flag right after starting.
        if (currentTick - BotStateManager.getInstance().getRunStartTick() < STARTUP_GRACE_TICKS) {
            resetAll(currentTick);
            nextResetTick = currentTick + 60 + (long)(Math.random() * 40);
            return;
        }

        if (currentTick >= nextResetTick) {
            resetAll(currentTick);
            nextResetTick = currentTick + 60 + (long)(Math.random() * 40);
            return;
        }
        runChecks(currentTick);
    }

    private void resetAll(long currentTick) {
        if (!PlayerInfo.isInWorld()) return;
        // InventoryChecker is self-timed (discrete 2s sampling + its own 10s startup exclusion, re-armed
        // off the run start tick), so it is intentionally NOT re-baselined here.
        toolChecker.reset();
        yawPitchChecker.reset(PlayerInfo.getYaw(), PlayerInfo.getPitch());
        pestChecker.reset();
        lastX = PlayerInfo.getX();
        lastZ = PlayerInfo.getZ();
        stallTicks = 0;
    }

    private void runChecks(long currentTick) {
        if (!PlayerInfo.isInWorld()) return;

        BotConfig cfg = BotConfig.getInstance();
        BotStateManager state = BotStateManager.getInstance();
        String heldItem   = PlayerInfo.getHeldItem().displayName();  // for readable logs
        String heldItemId = PlayerInfo.getHeldItem().skyblockId();   // stable identity for the check

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

        // ── Soft-stop flags: continue ~1s, then stop with keys + mouse freed ──
        // yaw/pitch change, held-item change, and no-movement all funnel into one behaviour:
        // a brief continuation then a clean stop (see BotStateManager.requestSoftStop). The alert
        // fires once (gated on !isStopPending) so we don't spam while the stop is pending.

        // Inventory checker (no crop/inventory change for 6s) — treat as a soft stop too.
        if (cfg.isInventoryCheckerEnabled() && !state.isStopPending()
                && !inventoryChecker.check(currentTick, state.getRunStartTick())) {
            BotLogger.getInstance().logWarn("InventoryChecker: No inventory changes across 3 samples (~4s) — soft stop");
            cfg.getWarnAlertSound().play();
            state.requestSoftStop("no inventory change");
        }

        // Held-item change.
        if (cfg.isToolCheckerEnabled() && !state.isStopPending() && !toolChecker.check(heldItemId)) {
            BotLogger.getInstance().logWarn("ToolChecker: Held item changed from '"
                    + state.getInitialTool() + "' to '" + heldItem + "' — soft stop");
            cfg.getWarnAlertSound().play();
            state.requestSoftStop("held item changed");
        }

        // Yaw/pitch change — a server-forced look (no mouse delta accompanies it, so the tab-in guard
        // never touches it). Alert + soft stop.
        if (cfg.isYawPitchCheckerEnabled() && !state.isStopPending()
                && !yawPitchChecker.check(PlayerInfo.getYaw(), PlayerInfo.getPitch())) {
            BotLogger.getInstance().logWarn("YawPitchChecker: Rotation changed unexpectedly — soft stop");
            DisplayActions.showTitle("§c⚠ LOOK CHANGED", "§eStopping shortly", 3, 40, 10);
            cfg.getWarnAlertSound().play();
            state.requestSoftStop("look changed");
        }

        // No-movement — the player isn't moving when the bot should be walking the path.
        if (cfg.isMovementCheckerEnabled() && !state.isStopPending()) {
            double x = PlayerInfo.getX(), z = PlayerInfo.getZ();
            if (!Double.isNaN(lastX)) {
                if (Math.abs(x - lastX) + Math.abs(z - lastZ) < 0.02) stallTicks++;
                else stallTicks = 0;
                if (stallTicks >= NO_MOVE_TICKS) {
                    BotLogger.getInstance().logWarn("MovementChecker: No movement for ~2s — soft stop");
                    cfg.getWarnAlertSound().play();
                    state.requestSoftStop("no movement");
                    stallTicks = 0;
                }
            }
            lastX = x;
            lastZ = z;
        }

        // ── Pest checker (warn only) ──────────────────────────────────────────
        if (cfg.isPestCheckerEnabled() && !pestChecker.check(currentTick)) {
            BotLogger.getInstance().logWarn("PestChecker: Pest count "
                    + state.getPestCount() + " >= threshold");
            cfg.getWarnAlertSound().play();
        }
    }

}

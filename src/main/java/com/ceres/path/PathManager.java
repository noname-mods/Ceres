package com.ceres.path;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotStateManager;
import com.playerapi.ChatActions;
import com.playerapi.MovementActions;
import com.playerapi.PlayerInfo;
import com.playerapi.Scheduler;
import java.util.ArrayList;
import java.util.List;

/** Handles path following logic. */
public class PathManager {

    private static final PathManager INSTANCE = new PathManager();

    private int messUpTimer = 0;

    /** Scheduler tick of the last cycle-start command sent — throttles spammy re-sends. */
    private long lastCycleCommandTick = Long.MIN_VALUE / 2;
    /** Minimum ticks between cycle-start commands (~3s). Stops super-short/empty paths from spamming
     *  the warp command as they loop-complete every tick. */
    private static final long CYCLE_COMMAND_COOLDOWN_TICKS = 60;

    private PathManager() {}

    public static PathManager getInstance() {
        return INSTANCE;
    }

    // ── Called every tick by CeresMod when state is RUNNING ──────────────────

    public void tick() {
        BotStateManager state = BotStateManager.getInstance();

        if (!state.isFollowingPath()) {
            startPathFollowing(state.getCurrentPathType());
            return;
        }

        if (messUpTimer > 0) {
            messUpTimer--;
            for (String key : List.of("forward", "back", "left", "right", "sprint", "sneak", "jump")) {
                MovementActions.releaseKey(key);
            }
            return;
        }

        followPathStep();
    }

    // ── Path following ────────────────────────────────────────────────────────

    private void followPathStep() {
        BotStateManager state = BotStateManager.getInstance();
        List<Waypoint> path = state.getCurrentPath();

        if (path == null || path.isEmpty()) {
            state.setIsFollowingPath(false);
            return;
        }

        int idx = state.getCurrentPathIndex();
        if (idx >= path.size()) {
            BotConfig cfg = BotConfig.getInstance();
            if (cfg.isOneCycleMode()) {
                BotLogger.getInstance().logInfo("PathManager: One-cycle complete — alerting and stopping");
                // Stop FIRST (cancels scheduler), then play so sound is scheduled fresh after cancelAll
                state.stopBot();
                cfg.getCycleCompleteSound().play();
            } else {
                state.setCurrentPathIndex(0);
                BotLogger.getInstance().logInfo("PathManager: Path loop completed, starting new cycle");
                sendCycleStartCommand(cfg);
            }
            return;
        }

        double playerX = PlayerInfo.getX();
        double playerZ = PlayerInfo.getZ();

        Waypoint target = path.get(idx);

        if (PathUtils.hasReachedTarget(playerX, playerZ, target.x, target.z, 0.5)) {
            for (String key : List.of("forward", "back", "left", "right", "sprint", "sneak", "jump")) {
                MovementActions.releaseKey(key);
            }
            state.setCurrentPathIndex(idx + 1);
            BotLogger.getInstance().logDebug("PathManager: Reached waypoint " + idx);
            return;
        }

        moveTowardsPoint(target);
        attemptMessUp();
    }

    private void moveTowardsPoint(Waypoint target) {
        List<String> keysToPress;

        if (target.hasForcedKeys()) {
            keysToPress = target.forcedKeys;
        } else {
            // No keys set — bot holds position until within reach of waypoint
            keysToPress = List.of();
        }

        // Use per-path sprint setting from PathConfig
        PathType currentType = BotStateManager.getInstance().getCurrentPathType();
        if (PathConfig.getInstance().isSprintEnabled(currentType)) {
            keysToPress = new ArrayList<>(keysToPress);
            keysToPress.add("sprint");
        }

        for (String key : List.of("forward", "back", "left", "right")) {
            if (!keysToPress.contains(key)) MovementActions.releaseKey(key);
        }
        for (String key : keysToPress) {
            MovementActions.pressKey(key);
        }
    }

    /**
     * Occasionally simulate a brief human-like pause (anti-detection).
     * Probability ~once every 2.5 hours.
     */
    private void attemptMessUp() {
        if (Math.random() < 1.0 / (100.0 * 60.0 * 30.0)) {
            MovementActions.releaseAll();
            messUpTimer = 10 + (int)(Math.random() * 20);
            BotLogger.getInstance().logDebug("PathManager: anti-detection pause for " + messUpTimer + " ticks");
        }
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

    public void startPathFollowing(PathType pathType) {
        List<Waypoint> path = PathConfig.getInstance().getPathPoints(pathType);
        if (path.isEmpty()) {
            BotLogger.getInstance().logWarn("PathManager: No waypoints for " + pathType);
            return;
        }

        BotStateManager state = BotStateManager.getInstance();
        state.setCurrentPath(new ArrayList<>(path));
        state.setCurrentPathIndex(0);
        state.setCurrentPathType(pathType);
        state.setIsFollowingPath(true);
        messUpTimer = 0;

        BotConfig cfg = BotConfig.getInstance();
        sendCycleStartCommand(cfg);

        MovementActions.pressKey("attack");
        if (cfg.isSneakOnPathStart()) {
            MovementActions.pressKey("sneak");
            Scheduler.schedule(5, () -> MovementActions.releaseKey("sneak"));
        }

        BotLogger.getInstance().logInfo("PathManager: Started following " + pathType
                + " (" + path.size() + " waypoints)");
    }

    private void sendCycleStartCommand(BotConfig cfg) {
        String cmd = cfg.getCycleRestartCommand().trim();
        if (cmd.isEmpty()) return;

        // Cooldown: a super-short (or unset) path loop-completes every tick, which would otherwise
        // re-send the warp command each time. Cap it to at most once per cooldown window.
        long now = Scheduler.getCurrentTick();
        if (now - lastCycleCommandTick < CYCLE_COMMAND_COOLDOWN_TICKS) {
            BotLogger.getInstance().logDebug(
                    "PathManager: cycle-start command suppressed (cooldown) — path too short?");
            return;
        }
        lastCycleCommandTick = now;

        String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        // Small delay so the command fires after any state changes settle
        Scheduler.schedule(5, () -> {
            ChatActions.sendCommand(cleanCmd);
            BotLogger.getInstance().logDebug("PathManager: Sent cycle-start command: " + cleanCmd);
        });
    }

}

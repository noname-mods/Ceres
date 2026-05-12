package com.ceres.core;

import com.ceres.path.PathType;
import com.ceres.path.Waypoint;
import com.playerapi.MovementActions;
import com.playerapi.PlayerInfo;
import com.playerapi.Scheduler;

import java.util.ArrayDeque;
import java.util.List;

/** Central singleton holding all runtime bot state. */
public class BotStateManager {

    private static final BotStateManager INSTANCE = new BotStateManager();

    private BotState currentState = BotState.STOPPED;

    private PathType currentPathType = PathType.PRIMARY;
    private boolean isFollowingPath = false;
    private int currentPathIndex = 0;
    private List<Waypoint> currentPath = null;

    private String currentArea = "";
    private int pestCount = 0;
    private String plotsText            = "";
    private String sprayText            = "";
    private String pestRepellentTimerText = "";
    private String bonusText            = "";
    private String sprayCooldownText    = "";
    private String bonusPestChanceText  = "";

    private boolean guiVisible = false;
    private boolean checksBypassed = false;
    private boolean checkFailed = false;
    private String initialTool = "";

    /** Name of the last profile loaded into PathConfig. "Custom" if paths were edited manually. */
    private String activeProfileName = "Custom";

    // ── Blocks-per-second sliding window ──────────────────────────────────────
    /** Ticks (from Scheduler) at which each block break was recorded. */
    private final ArrayDeque<Long> blockBreakTicks = new ArrayDeque<>();
    /** 30 seconds expressed in ticks (20 ticks/s). */
    private static final int BPS_WINDOW_TICKS = 600;
    /** Tick at which the current RUNNING session started (set by startBot / resumeBot). */
    private long runStartTick = 0;

    private BotStateManager() {}

    public static BotStateManager getInstance() { return INSTANCE; }

    // ── Bot lifecycle ─────────────────────────────────────────────────────────

    public void startBot(PathType pathType) {
        if (!checksBypassed && !BotConfig.getInstance().isBypassAreaCheck()
                && !currentArea.equalsIgnoreCase("Garden")) {
            BotLogger.getInstance().logWarn("Not in expected area, cannot start");
            return;
        }
        currentState = BotState.RUNNING;
        currentPathType = pathType;
        isFollowingPath = false;
        currentPathIndex = 0;
        checkFailed = false;
        blockBreakTicks.clear();
        runStartTick = Scheduler.getCurrentTick();
        MovementActions.setActive(true);
        initialTool = PlayerInfo.getHeldItem().displayName();
        BotLogger.getInstance().logInfo("Started on " + pathType);
    }

    public void pauseBot() {
        if (currentState == BotState.RUNNING) {
            currentState = BotState.PAUSED;
            blockBreakTicks.clear(); // discard data from the paused session
            MovementActions.releaseAll();
            BotLogger.getInstance().logInfo("Paused");
        }
    }

    public void resumeBot() {
        if (currentState == BotState.PAUSED) {
            currentState = BotState.RUNNING;
            blockBreakTicks.clear();          // fresh window for the resumed session
            runStartTick = Scheduler.getCurrentTick();
            MovementActions.pressKey("attack"); // re-engage attack released during pause
            BotLogger.getInstance().logInfo("Resumed");
        }
    }

    public void stopBot() {
        currentState = BotState.STOPPED;
        isFollowingPath = false;
        currentPathIndex = 0;
        currentPath = null;
        blockBreakTicks.clear();
        MovementActions.releaseAll();
        MovementActions.setActive(false);
        Scheduler.cancelAll();
        BotLogger.getInstance().logInfo("Stopped");
    }

    /**
     * Area-change stop — used when the player is teleported out of the Garden.
     *
     * Transitions to STOPPED immediately so MouseLookMixin releases its lock
     * on the mouse and clicks at once. Bot-scheduled tasks are cancelled, but
     * movement keys are intentionally NOT released right away: the player coasts
     * for ~1 second in whatever direction the bot was heading, giving a natural
     * deceleration and instant full control rather than a dead-freeze.
     *
     * After 20 ticks (≈1 second) the held keys are cleared and the movement
     * input system is fully handed back to the player.
     */
    public void emergencyStop() {
        currentState = BotState.STOPPED;
        isFollowingPath = false;
        currentPathIndex = 0;
        currentPath = null;
        blockBreakTicks.clear();
        Scheduler.cancelAll();
        // Schedule must come AFTER cancelAll so it is not itself cancelled.
        Scheduler.schedule(20, () -> {
            MovementActions.releaseAll();
            MovementActions.setActive(false);
        });
        BotLogger.getInstance().logInfo("Stopped (area change — releasing keys in ~1s)");
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public BotState getCurrentState()                  { return currentState; }
    public PathType getCurrentPathType()               { return currentPathType; }
    public void setCurrentPathType(PathType t)         { currentPathType = t; }
    public boolean isFollowingPath()                   { return isFollowingPath; }
    public void setIsFollowingPath(boolean v)          { isFollowingPath = v; }
    public int getCurrentPathIndex()                   { return currentPathIndex; }
    public void setCurrentPathIndex(int i)             { currentPathIndex = i; }
    public List<Waypoint> getCurrentPath()             { return currentPath; }
    public void setCurrentPath(List<Waypoint> path)    { currentPath = path; }
    public String getCurrentArea()                     { return currentArea; }
    public void setCurrentArea(String area)            { currentArea = area; }
    public int getPestCount()                          { return pestCount; }
    public void setPestCount(int count)                { pestCount = count; }
    public String getPlotsText()                       { return plotsText; }
    public void setPlotsText(String t)                 { plotsText = t; }
    public String getSprayText()                       { return sprayText; }
    public void setSprayText(String t)                 { sprayText = t; }
    public String getPestRepellentTimerText()          { return pestRepellentTimerText; }
    public void setPestRepellentTimerText(String t)    { pestRepellentTimerText = t; }
    public String getBonusText()                       { return bonusText; }
    public void setBonusText(String t)                 { bonusText = t; }
    public String getSprayCooldownText()               { return sprayCooldownText; }
    public void setSprayCooldownText(String t)         { sprayCooldownText = t; }
    public String getBonusPestChanceText()             { return bonusPestChanceText; }
    public void setBonusPestChanceText(String t)       { bonusPestChanceText = t; }
    public boolean isGuiVisible()                      { return guiVisible; }
    public void setGuiVisible(boolean v)               { guiVisible = v; }
    public void toggleGuiVisible()                     { guiVisible = !guiVisible; }
    public boolean isCheckFailed()                     { return checkFailed; }
    public void setCheckFailed(boolean v)              { checkFailed = v; }
    public String getInitialTool()                     { return initialTool; }
    public boolean isChecksBypassed()                  { return checksBypassed; }
    public void setChecksBypassed(boolean v)           { checksBypassed = v; }
    public void toggleChecksBypassed()                 { checksBypassed = !checksBypassed; }
    public String getActiveProfileName()               { return activeProfileName; }
    public void setActiveProfileName(String name)      { activeProfileName = (name != null && !name.isBlank()) ? name : "Custom"; }

    // ── Bps ───────────────────────────────────────────────────────────────────

    /** Record that a block was just broken. Prunes stale entries from the window. */
    public void recordBlockBroken(long currentTick) {
        blockBreakTicks.addLast(currentTick);
        long cutoff = currentTick - BPS_WINDOW_TICKS;
        while (!blockBreakTicks.isEmpty() && blockBreakTicks.peekFirst() <= cutoff)
            blockBreakTicks.pollFirst();
    }

    /**
     * Returns the average blocks broken per second.
     * Divides by the actual elapsed time since the session started (capped at 30 s) so early
     * readings are accurate instead of artificially low while the window fills up.
     */
    public double getBlocksPerSecond() {
        long currentTick = Scheduler.getCurrentTick();
        long cutoff = currentTick - BPS_WINDOW_TICKS;
        while (!blockBreakTicks.isEmpty() && blockBreakTicks.peekFirst() <= cutoff)
            blockBreakTicks.pollFirst();
        double elapsedSeconds = Math.min((currentTick - runStartTick) / 20.0, 30.0);
        if (elapsedSeconds <= 0) return 0.0;
        return blockBreakTicks.size() / elapsedSeconds;
    }
}

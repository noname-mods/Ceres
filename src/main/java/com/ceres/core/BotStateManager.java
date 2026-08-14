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
    private boolean stopPending = false;
    private String initialTool = "";
    /** SkyBlock internal id of the tool held at start — the stable identity for the tool-change check. */
    private String initialToolId = "";

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
        stopPending = false;
        blockBreakTicks.clear();
        runStartTick = Scheduler.getCurrentTick();
        MovementActions.setActive(true);
        initialTool = PlayerInfo.getHeldItem().displayName();
        initialToolId = PlayerInfo.getHeldItem().skyblockId();
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
        stopPending = false;
        blockBreakTicks.clear();
        MovementActions.releaseAll();
        MovementActions.setActive(false);
        Scheduler.cancelAll();
        BotLogger.getInstance().logInfo("Stopped");
    }

    /**
     * Flag-triggered soft stop: keep running for ~1 second, then stop with all keys + the mouse freed.
     * Used for the yaw/pitch, held-item, and no-movement flags — a brief natural continuation rather
     * than a dead freeze. The mouse unlocks cleanly (no snap) thanks to the accumulator fix in
     * {@link com.ceres.mixin.MouseLookMixin}. Idempotent: only the first call arms the stop.
     */
    public void requestSoftStop(String reason) {
        if (currentState != BotState.RUNNING || stopPending) return;
        stopPending = true;
        int delay = 40 + (int) (Math.random() * 20); // ~2–3s
        BotLogger.getInstance().logWarn("Flag (" + reason + ") — stopping in ~" + (delay * 50) + "ms");
        Scheduler.schedule(delay, this::stopBot);
    }

    public boolean isStopPending() { return stopPending; }

    /**
     * Area-change stop — used when the player is teleported out of the Garden.
     *
     * Stops <b>instantly</b>: movement keys stop being held and the mouse unlocks the moment we leave
     * the Garden. We do NOT force the player's position — the keys simply release, which for players
     * using area-specific keybinds reads naturally as the keybinds no longer applying. (Was a ~1s coast;
     * now instant per design.)
     */
    public void emergencyStop() {
        currentState = BotState.STOPPED;
        isFollowingPath = false;
        currentPathIndex = 0;
        currentPath = null;
        stopPending = false;
        blockBreakTicks.clear();
        Scheduler.cancelAll();
        MovementActions.releaseAll();      // instant — no coast
        MovementActions.setActive(false);
        BotLogger.getInstance().logInfo("Stopped (area change — keys released instantly)");
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
    public String getInitialToolId()                   { return initialToolId; }
    public long   getRunStartTick()                    { return runStartTick; }
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

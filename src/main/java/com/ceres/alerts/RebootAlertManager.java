package com.ceres.alerts;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.core.BotStateManager;
import com.playerapi.SoundActions;

/**
 * Detects the Hypixel "scheduled reboot" chat message and plays a persistent
 * alarm sound until the player leaves the Garden area.
 *
 * Detection: a server (no-sender) chat message containing {@value TRIGGER_TEXT}.
 * Hypixel sends:  §c[Important] §eThis server will restart soon: §bScheduled Reboot
 * After stripping: [Important] This server will restart soon: Scheduled Reboot
 *
 * Dismissal: the alarm stops automatically when the tab-list area changes away
 * from "Garden" — any destination (Hub, lobby, etc.) counts.
 *
 * Sound looping is driven by a tick counter inside {@link #tick()} rather than
 * the Scheduler, so it is immune to Scheduler.cancelAll() called by stopBot().
 */
public class RebootAlertManager {

    private static final RebootAlertManager INSTANCE = new RebootAlertManager();

    private static final String TRIGGER_TEXT = "This server will restart soon";

    private boolean alertActive = false;
    /** Counts up each tick; sound plays when it reaches the configured interval. */
    private int ticksSinceLastPlay = Integer.MAX_VALUE; // ensures first play is immediate

    private RebootAlertManager() {}

    public static RebootAlertManager getInstance() { return INSTANCE; }

    // ── Called by CeresMod ────────────────────────────────────────────────────

    /**
     * Called from PlayerAPIEvents.CHAT_RECEIVED.
     * Server messages arrive with an empty sender; player messages have a name.
     */
    public void onChatReceived(String sender, String message) {
        if (alertActive) return;
        if (!BotConfig.getInstance().isRebootAlertEnabled()) return;
        if (!sender.isEmpty()) return;                        // server messages only
        if (!message.contains(TRIGGER_TEXT)) return;

        BotLogger.getInstance().logWarn(
                "Server reboot detected — alarm will play until you leave the Garden.");
        alertActive = true;
        ticksSinceLastPlay = Integer.MAX_VALUE; // play on the very next tick
    }

    /**
     * Called every tick from CeresMod.onTick().
     * Manages sound looping and watches for the player leaving the Garden.
     */
    public void tick() {
        if (!alertActive) return;

        // Allow the user to disable mid-alarm via the config screen.
        if (!BotConfig.getInstance().isRebootAlertEnabled()) {
            cancelAlert();
            return;
        }

        // Stop once the player has left the Garden.
        // Only act when the area is known (non-empty) — avoids false positives
        // during the brief window before the tab list has populated after a warp.
        String area = BotStateManager.getInstance().getCurrentArea();
        if (!area.isEmpty() && !area.equalsIgnoreCase("Garden")) {
            cancelAlert();
            return;
        }

        // Drive sound looping via a simple tick counter.
        BotConfig.AlarmSound sound = BotConfig.getInstance().getRebootAlertSound();
        int interval = Math.max(1, sound.intervalTicks);
        ticksSinceLastPlay++;
        if (ticksSinceLastPlay >= interval) {
            ticksSinceLastPlay = 0;
            SoundActions.playById(sound.soundId, (float) sound.volume, (float) sound.pitch);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void cancelAlert() {
        alertActive = false;
        ticksSinceLastPlay = Integer.MAX_VALUE;
        BotLogger.getInstance().logInfo("Reboot alarm dismissed — left the Garden.");
    }

    public boolean isAlertActive() { return alertActive; }
}

package com.ceres.mixin;

import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks player mouse input while the bot is actively running, and keeps camera control clean around
 * the moment the lock is released.
 *
 * <h2>Why the accumulator is zeroed (fixes the "stop snap")</h2>
 * In MC 26.1.2 mouse deltas are gathered in {@code accumulatedDX/DY} by {@code onMove()} and flushed to
 * the camera each tick by {@code handleAccumulatedMovement()}. Cancelling that flush freezes the camera,
 * but the deltas keep piling up — so the instant the bot stops, one giant flush snaps the view. We fix
 * that at the root by zeroing the accumulators every frame while locked: nothing is ever pending, so
 * releasing the lock (on stop or a flagged soft-stop) moves the camera by zero.
 *
 * <h2>Tab-back spike guard (issue 3)</h2>
 * A large delta can still arrive <em>after</em> the mouse is freed — e.g. the player alt-tabs back in,
 * producing a huge one-frame jump that clearing the accumulator can't pre-empt. For a short window after
 * unlock we drop any single-frame delta above {@link #SPIKE_DELTA}, so windowing back in doesn't jerk the
 * view. This keys purely on <b>mouse-delta magnitude</b>, so a server-forced look (which carries no mouse
 * delta) is never affected.
 */
@Mixin(MouseHandler.class)
public class MouseLookMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    /** Was the look locked on the previous flush? Used to detect the unlock transition. */
    @Unique private boolean ceres$wasLocked = false;
    /** Until this time (ms), drop tab-back-shaped delta spikes. */
    @Unique private long ceres$spikeGuardUntil = 0L;

    /**
     * Single-frame delta magnitude treated as a tab-back artifact (raw cursor units). Set high so only a
     * tab-back-scale jump is swallowed — a normal fast flick must NOT trip it (50 was far too sensitive).
     */
    @Unique private static final double SPIKE_DELTA = 300.0;
    /** How long the spike guard stays armed after the lock releases. */
    @Unique private static final long SPIKE_GUARD_MS = 10_000L;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void ceres$blockLookWhenRunning(CallbackInfo ci) {
        boolean locked = BotStateManager.getInstance().getCurrentState() == BotState.RUNNING;

        if (locked) {
            // Discard pending deltas so they never accumulate — the root fix for the release snap.
            accumulatedDX = 0;
            accumulatedDY = 0;
            ceres$wasLocked = true;
            ci.cancel();
            return;
        }

        if (ceres$wasLocked) {
            // Just unlocked (normal stop or a flagged soft-stop). Drop anything pending and skip the
            // next move event, then arm the tab-back spike guard for a short window.
            ceres$wasLocked = false;
            accumulatedDX = 0;
            accumulatedDY = 0;
            ((MouseHandler) (Object) this).setIgnoreFirstMove();
            ceres$spikeGuardUntil = System.currentTimeMillis() + SPIKE_GUARD_MS;
            return;
        }

        // Tab-back guard: within the window, swallow a single massive delta (leaves normal movement alone).
        if (System.currentTimeMillis() < ceres$spikeGuardUntil
                && (Math.abs(accumulatedDX) > SPIKE_DELTA || Math.abs(accumulatedDY) > SPIKE_DELTA)) {
            accumulatedDX = 0;
            accumulatedDY = 0;
        }
    }

    /** Block hotbar scroll wheel — only in-game, not while any screen is open. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ceres$blockScrollWhenRunning(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().screen == null
                && BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
        }
    }

    /**
     * Block left (0) and right (1) click — only in-game, not while any screen is open.
     * In MC 26.1.2 onPress was replaced by onButton(long, MouseButtonInfo, int).
     * Middle click and other buttons are left alone.
     */
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void ceres$blockClicksWhenRunning(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        int button = buttonInfo.button();
        if (Minecraft.getInstance().screen == null
                && (button == 0 || button == 1)
                && BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
        }
    }
}

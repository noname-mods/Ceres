package com.ceres.mixin;

import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks player mouse input while the bot is actively running.
 * Pausing, stopping, or a checker/one-cycle completion restores full control.
 * Programmatic changes via LookActions are not affected.
 */
@Mixin(MouseHandler.class)
public class MouseLookMixin {

    /**
     * Block camera movement.
     * In MC 26.1.2 mouse deltas are accumulated via onMove() and then flushed
     * each tick by handleAccumulatedMovement() — cancelling that method is the
     * correct hook to freeze the camera.
     */
    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void ceres$blockLookWhenRunning(CallbackInfo ci) {
        if (BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
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

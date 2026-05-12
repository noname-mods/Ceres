package com.ceres.mixin;

import com.ceres.core.BotState;
import com.ceres.core.BotStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locks player mouse input while the bot is actively running.
 * Pausing, stopping, or a checker/one-cycle completion restores full control.
 * Programmatic changes via LookActions are not affected.
 */
@Mixin(Mouse.class)
public class MouseLookMixin {

    /** Block camera movement — fires only in-game (MC skips updateMouse when a screen is open). */
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void ceres$blockLookWhenRunning(double timeDelta, CallbackInfo ci) {
        if (BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
        }
    }

    /** Block hotbar scroll wheel — only in-game, not while any screen is open. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void ceres$blockScrollWhenRunning(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen == null
                && BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
        }
    }

    /**
     * Block left (0) and right (1) click — only in-game, not while any screen is open.
     * Middle click and other buttons are left alone.
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void ceres$blockClicksWhenRunning(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        int button = mouseInput.button();
        if (MinecraftClient.getInstance().currentScreen == null
                && (button == 0 || button == 1)
                && BotStateManager.getInstance().getCurrentState() == BotState.RUNNING) {
            ci.cancel();
        }
    }
}

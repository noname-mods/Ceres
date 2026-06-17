package com.ceres.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple modal warning screen.
 * Usage: Minecraft.getInstance().setScreen(new WarningScreen("message", parent))
 */
public class WarningScreen extends Screen {

    private final Screen parent;
    private final String message;

    public WarningScreen(String message, Screen parent) {
        super(Component.literal("Warning"));
        this.message = message;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 100, btnH = 20;
        int btnX = (width - btnW) / 2;
        int btnY = height / 2 + 30;
        addRenderableWidget(Button.builder(Component.literal("OK"),
                btn -> onClose())
                .bounds(btnX, btnY, btnW, btnH)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);

        // Dark overlay
        context.fill(width / 2 - 120, height / 2 - 50, width / 2 + 120, height / 2 + 60, 0xDD000000);

        context.centeredText(font, "⚠ Warning", width / 2, height / 2 - 40, 0xFFFF5555);
        context.centeredText(font, message, width / 2, height / 2 - 10, 0xFFFFFFFF);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

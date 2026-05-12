package com.ceres.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Simple modal warning screen.
 * Usage: MinecraftClient.getInstance().setScreen(new WarningScreen("message", parent))
 */
public class WarningScreen extends Screen {

    private final Screen parent;
    private final String message;

    public WarningScreen(String message, Screen parent) {
        super(Text.literal("Warning"));
        this.message = message;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 100, btnH = 20;
        int btnX = (width - btnW) / 2;
        int btnY = height / 2 + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"),
                btn -> close())
                .dimensions(btnX, btnY, btnW, btnH)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        // Dark overlay
        context.fill(width / 2 - 120, height / 2 - 50, width / 2 + 120, height / 2 + 60, 0xDD000000);

        context.drawCenteredTextWithShadow(textRenderer, "⚠ Warning", width / 2, height / 2 - 40, 0xFFFF5555);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2 - 10, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

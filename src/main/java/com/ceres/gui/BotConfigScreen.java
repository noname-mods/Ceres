package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Legacy configuration screen — no longer reachable in normal play.
 *
 * The current configuration UI is {@link CeresConfigScreen} (YACL-based),
 * opened via /ceres, the "Open Config" keybind, or the ModMenu "Config" button.
 *
 * Retained as a reference; do not add new settings here.
 */
public class BotConfigScreen extends Screen {

    private static final String[] LOG_LEVEL_NAMES = {"ERROR", "WARN", "INFO", "DEBUG"};

    private final Screen parent;
    private final BotConfig config = BotConfig.getInstance();

    public BotConfigScreen(Screen parent) {
        super(Component.literal("Ceres Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int startY = height / 2 - 80;
        int rowH = 28;

        // ── Min Pest Count ───────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("−"),
                btn -> config.setMinPestCount(config.getMinPestCount() - 1))
                .bounds(cx - 70, startY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                btn -> config.setMinPestCount(config.getMinPestCount() + 1))
                .bounds(cx + 50, startY, 20, 20).build());

        // ── Log Level ────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.literal("Log: " + currentLogLevelName()),
                btn -> {
                    int next = (config.getLogLevel() % 4) + 1; // cycle 1–4
                    config.setLogLevel(next);
                    btn.setMessage(Component.literal("Log: " + currentLogLevelName()));
                })
                .bounds(cx - 60, startY + rowH, 120, 20).build());

        // ── Repellent reapply toggle ─────────────────────────
        addRenderableWidget(Button.builder(
                Component.literal("Repellent Reapply: " + (config.isRepellentReapplyEnabled() ? "ON" : "OFF")),
                btn -> {
                    config.setRepellentReapplyEnabled(!config.isRepellentReapplyEnabled());
                    btn.setMessage(Component.literal("Repellent Reapply: " +
                            (config.isRepellentReapplyEnabled() ? "ON" : "OFF")));
                })
                .bounds(cx - 80, startY + rowH * 2, 160, 20).build());

        // ── Done button ──────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(cx - 50, startY + rowH * 4, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);

        context.centeredText(font, "Ceres Config",
                width / 2, height / 2 - 100, 0xFFFFFFFF);

        int cx = width / 2;
        int startY = height / 2 - 80;

        // Labels
        context.centeredText(font,
                "Min Pest Count: " + config.getMinPestCount(),
                cx, startY + 4, 0xFFFFFFFF);
        // (other rows are rendered by the button labels themselves)

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        config.save();
        BotLogger.getInstance().logInfo("BotConfigScreen: Config saved");
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String currentLogLevelName() {
        int level = config.getLogLevel();
        if (level >= 1 && level <= LOG_LEVEL_NAMES.length) {
            return LOG_LEVEL_NAMES[level - 1];
        }
        return "?";
    }
}

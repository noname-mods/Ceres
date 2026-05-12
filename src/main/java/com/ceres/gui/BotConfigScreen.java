package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
        super(Text.literal("Ceres Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int startY = height / 2 - 80;
        int rowH = 28;

        // ── Min Pest Count ───────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                btn -> config.setMinPestCount(config.getMinPestCount() - 1))
                .dimensions(cx - 70, startY, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                btn -> config.setMinPestCount(config.getMinPestCount() + 1))
                .dimensions(cx + 50, startY, 20, 20).build());

        // ── Log Level ────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Log: " + currentLogLevelName()),
                btn -> {
                    int next = (config.getLogLevel() % 4) + 1; // cycle 1–4
                    config.setLogLevel(next);
                    btn.setMessage(Text.literal("Log: " + currentLogLevelName()));
                })
                .dimensions(cx - 60, startY + rowH, 120, 20).build());

        // ── Repellent reapply toggle ─────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Repellent Reapply: " + (config.isRepellentReapplyEnabled() ? "ON" : "OFF")),
                btn -> {
                    config.setRepellentReapplyEnabled(!config.isRepellentReapplyEnabled());
                    btn.setMessage(Text.literal("Repellent Reapply: " +
                            (config.isRepellentReapplyEnabled() ? "ON" : "OFF")));
                })
                .dimensions(cx - 80, startY + rowH * 2, 160, 20).build());

        // ── Done button ──────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(cx - 50, startY + rowH * 4, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, "Ceres Config",
                width / 2, height / 2 - 100, 0xFFFFFFFF);

        int cx = width / 2;
        int startY = height / 2 - 80;
        int rowH = 28;

        // Labels
        context.drawCenteredTextWithShadow(textRenderer,
                "Min Pest Count: " + config.getMinPestCount(),
                cx, startY + 4, 0xFFFFFFFF);
        // (other rows are rendered by the button labels themselves)

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        config.save();
        BotLogger.getInstance().logInfo("BotConfigScreen: Config saved");
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
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

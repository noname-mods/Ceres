package com.ceres.gui;

import com.ceres.core.BotConfig;
import com.ceres.core.BotConfig.AlarmSound;
import com.ceres.core.BotLogger;
import com.ceres.path.CropToolMapper;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Central Ceres configuration screen built with YACL.
 * Opened via /ceres, the ModMenu "Config" button, or the "Open Config" keybind.
 */
public class CeresConfigScreen {

    private CeresConfigScreen() {}

    public static Screen create(Screen parent) {
        BotConfig cfg = BotConfig.getInstance();

        return YetAnotherConfigLib.createBuilder()
            .title(Text.literal("Ceres Configuration"))

            // ── Bot Settings ──────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Bot Settings"))
                .tooltip(Text.literal("General bot behaviour settings."))

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Sneak on Path Start"))
                    .description(OptionDescription.of(Text.literal(
                        "Briefly sneak when starting a path run to snap orientation before moving.")))
                    .binding(true, cfg::isSneakOnPathStart, cfg::setSneakOnPathStart)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Pest Repellent Reapply"))
                    .description(OptionDescription.of(Text.literal(
                        "Automatically pause and reapply pest repellent when the timer expires.")))
                    .binding(true, cfg::isRepellentReapplyEnabled, cfg::setRepellentReapplyEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("One Cycle Mode"))
                    .description(OptionDescription.of(Text.literal(
                        "When enabled, the bot completes one full path cycle then plays an alert and stops " +
                        "instead of looping. Useful for single-pass tasks or scheduled restarts.")))
                    .binding(false, cfg::isOneCycleMode, cfg::setOneCycleMode)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<String>createBuilder()
                    .name(Text.literal("Cycle Start Command"))
                    .description(OptionDescription.of(Text.literal(
                        "Command sent at the start of every cycle (including the first) to reposition " +
                        "the player before the path begins. In One Cycle Mode it fires once at the " +
                        "start only. Leave blank to disable. Do not include the leading /.")))
                    .binding("warp garden", cfg::getCycleRestartCommand, cfg::setCycleRestartCommand)
                    .controller(StringControllerBuilder::create)
                    .build())

                .build())

            // ── Auto-Load ─────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Auto-Load"))
                .tooltip(Text.literal("Automatically load a saved profile based on the tool you are holding when the bot starts."))

                .option(LabelOption.createBuilder()
                    .line(Text.literal("When you press Start, Ceres checks your held tool and loads the"))
                    .line(Text.literal("matching saved profile automatically — no manual switching needed."))
                    .line(Text.literal("§7Reforged or tiered tools (e.g. \"Blessed Euclid's Wheat Hoe Mk. II\")"))
                    .line(Text.literal("§7are matched by substring, so tiers and reforges are handled."))
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Enable Auto-Load"))
                    .description(OptionDescription.of(Text.literal(
                        "Master toggle. When off, no profile is auto-loaded on start " +
                        "regardless of the per-crop settings below.")))
                    .binding(true, cfg::isAutoLoadEnabled, cfg::setAutoLoadEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(LabelOption.createBuilder()
                    .line(Text.literal("§8─── Per-crop toggles (require Enable Auto-Load above) ───────"))
                    .build())

                .options(CropToolMapper.ALL_CROPS.stream()
                    .map(crop -> autoLoadCropOption(crop, cfg))
                    .toList())

                .build())

            // ── HUD Lines ─────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("HUD Lines"))
                .tooltip(Text.literal("Toggle individual rows in the Ceres HUD to reduce clutter."))

                .option(LabelOption.createBuilder()
                    .line(Text.literal("Show or hide each row in the overlay. Tab-list rows"))
                    .line(Text.literal("(Plots, Spray, etc.) are also hidden when the server"))
                    .line(Text.literal("isn't sending that data, regardless of this setting."))
                    .build())

                .option(hudLineOption("profile",    "Profile",    "The name of the currently loaded path profile.", cfg))
                .option(hudLineOption("area",       "Area",       "Current area read from the tab list.", cfg))
                .option(hudLineOption("xyz",        "XYZ",        "Player coordinates.", cfg))
                .option(hudLineOption("look",       "Look",       "Player yaw and pitch.", cfg))
                .option(hudLineOption("pests",      "Pests",      "Live pest count versus the alarm threshold.", cfg))
                .option(hudLineOption("plots",      "Plots",      "Plots line from the pest section of the tab list.", cfg))
                .option(hudLineOption("spray",      "Spray",      "Spray line from the pest section of the tab list.", cfg))
                .option(hudLineOption("repellent",  "Repellent",  "Pest repellent timer from the tab list.", cfg))
                .option(hudLineOption("bonus",      "Bonus",      "Bonus line from the pest section of the tab list.", cfg))
                .option(hudLineOption("cooldown",   "Cooldown",   "Pest spawn cooldown from the tab list. Shows the time before a new pest is allowed to spawn — a pest does not spawn automatically when it reaches zero, it just becomes possible. Useful for knowing when to pause and swap gear for bonus pest chance, or for tracking how long until the next pest could appear.", cfg))
                .option(hudLineOption("pest_chance","Pest Chance","Bonus pest chance line from the tab list.", cfg))
                .option(hudLineOption("path",       "Path",       "Current path type and waypoint progress. Only shown while running.", cfg))
                .option(hudLineOption("bps",        "Bps",        "Blocks broken per second (30-second rolling average). Only shown while running.", cfg))
                .option(hudLineOption("target",     "Target",     "Coordinates of the next waypoint. Only shown while running.", cfg))

                .build())

            // ── Checkers ──────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Checkers"))
                .tooltip(Text.literal("Safety check toggles and thresholds."))

                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("Min Pest Count for Alarm"))
                    .description(OptionDescription.of(Text.literal(
                        "Trigger the pest alarm when the tab-list pest count reaches this number.")))
                    .binding(4, cfg::getMinPestCount, cfg::setMinPestCount)
                    .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(1, 20)
                        .step(1))
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Inventory Checker"))
                    .description(OptionDescription.of(Text.literal(
                        "Stop the bot if the hotbar hasn't changed for 6 seconds (stuck detector).")))
                    .binding(true, cfg::isInventoryCheckerEnabled, cfg::setInventoryCheckerEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Tool Checker"))
                    .description(OptionDescription.of(Text.literal(
                        "Stop the bot if the held item changes unexpectedly.")))
                    .binding(true, cfg::isToolCheckerEnabled, cfg::setToolCheckerEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Yaw / Pitch Checker"))
                    .description(OptionDescription.of(Text.literal(
                        "Alert if the player's rotation drifts more than 1 degree.")))
                    .binding(true, cfg::isYawPitchCheckerEnabled, cfg::setYawPitchCheckerEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Pest Checker"))
                    .description(OptionDescription.of(Text.literal(
                        "Trigger the pest alarm when the pest count reaches the minimum.")))
                    .binding(true, cfg::isPestCheckerEnabled, cfg::setPestCheckerEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .build())

            // ── Sounds ────────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Sounds"))
                .tooltip(Text.literal("Customise alert sounds, pitch, volume and repeat behaviour."))

                .option(LabelOption.createBuilder()
                    .line(Text.literal("Sound IDs use the format §esound.category.name§r"))
                    .line(Text.literal("e.g. §eentity.player.levelup§r  or  §eblock.note_block.pling"))
                    .line(Text.literal("Browse all vanilla sounds at: §ehttps://misode.github.io/sounds/"))
                    .line(Text.literal("§7Namespace is always minecraft: for vanilla — omit it here."))
                    .build())

                // ── Cycle Complete (One-Cycle Mode) ───────────────────────────
                .option(LabelOption.createBuilder()
                    .line(Text.literal("§6─── Cycle Complete  §7(plays when One Cycle Mode finishes) ───"))
                    .build())

                .option(alarmSoundIdOption("Cycle Complete Sound",
                    AlarmSound.defaultStop().soundId,
                    cfg.getCycleCompleteSound(), cfg))

                .option(alarmVolumeOption("Cycle Complete Volume",
                    cfg.getCycleCompleteSound(), cfg))

                .option(alarmPitchOption("Cycle Complete Pitch",
                    cfg.getCycleCompleteSound(), cfg))

                .option(alarmRepeatOption("Cycle Complete Duration",
                    cfg.getCycleCompleteSound(), cfg))

                .option(alarmIntervalOption("Cycle Complete Interval",
                    cfg.getCycleCompleteSound(), cfg))

                // ── Stop Alert (checker hard-stops) ───────────────────────────
                .option(LabelOption.createBuilder()
                    .line(Text.literal("§c─── Stop Alert  §7(inventory checker, tool checker) ──────────"))
                    .build())

                .option(alarmSoundIdOption("Stop Alert Sound",
                    AlarmSound.defaultStop().soundId,
                    cfg.getStopAlertSound(), cfg))

                .option(alarmVolumeOption("Stop Alert Volume",
                    cfg.getStopAlertSound(), cfg))

                .option(alarmPitchOption("Stop Alert Pitch",
                    cfg.getStopAlertSound(), cfg))

                .option(alarmRepeatOption("Stop Alert Duration",
                    cfg.getStopAlertSound(), cfg))

                .option(alarmIntervalOption("Stop Alert Interval",
                    cfg.getStopAlertSound(), cfg))

                // ── Warn Alert (non-stop alerts) ──────────────────────────────
                .option(LabelOption.createBuilder()
                    .line(Text.literal("§e─── Warn Alert  §7(yaw/pitch checker, pest alert) ───────────"))
                    .build())

                .option(alarmSoundIdOption("Warn Alert Sound",
                    AlarmSound.defaultWarn().soundId,
                    cfg.getWarnAlertSound(), cfg))

                .option(alarmVolumeOption("Warn Alert Volume",
                    cfg.getWarnAlertSound(), cfg))

                .option(alarmPitchOption("Warn Alert Pitch",
                    cfg.getWarnAlertSound(), cfg))

                .option(alarmRepeatOption("Warn Alert Duration",
                    cfg.getWarnAlertSound(), cfg))

                .option(alarmIntervalOption("Warn Alert Interval",
                    cfg.getWarnAlertSound(), cfg))

                .build())

            // ── Keybinds ──────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Keybinds"))
                .tooltip(Text.literal("Remap keybinds in Options → Controls → Key Binds under \"Ceres\"."))

                .option(LabelOption.createBuilder()
                    .line(Text.literal("All Ceres keybinds can be rebound (including to NONE via Escape)"))
                    .line(Text.literal("in §eOptions → Controls → Key Binds §r→ \"Ceres\" category."))
                    .line(Text.literal(" "))
                    .line(Text.literal("Default bindings:"))
                    .line(Text.literal("  §e;§r  Toggle HUD        §e I §r  Open Path Editor"))
                    .line(Text.literal("  §eO§r  Start Primary     §e U §r  Start Secondary"))
                    .line(Text.literal("  §eP§r  Pause   §e J §r  Resume   §e K §r  Stop"))
                    .build())

                .build())

            // ── Updates ───────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Updates"))
                .tooltip(Text.literal("GitHub release update notifications."))

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Update Check"))
                    .description(OptionDescription.of(Text.literal(
                        "On every world join, Ceres checks GitHub for a newer release.\n" +
                        "If one is found, a single chat message is shown. No download occurs.\n\n" +
                        "Disable this if you are offline, on a restricted network, or\n" +
                        "simply do not want the notification.")))
                    .binding(true, cfg::isUpdateCheckEnabled, cfg::setUpdateCheckEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .build())

            // ── Developer ─────────────────────────────────────────────────────
            .category(ConfigCategory.createBuilder()
                .name(Text.literal("Developer").formatted(Formatting.RED))
                .tooltip(Text.literal("Testing options — do not use during normal operation."))

                .option(LabelOption.createBuilder()
                    .line(Text.literal("§c⚠ These settings may break things or cause unintended behaviour."))
                    .line(Text.literal("§7Only change these if you know what you are doing."))
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Debug Mode"))
                    .description(OptionDescription.of(Text.literal(
                        "Prints diagnostic messages to chat and the log file.\n" +
                        "Useful for diagnosing why /ceres may not open on some servers:\n" +
                        "green message = ClientCommandManager fired normally;\n" +
                        "no message = ALLOW_COMMAND fallback fired (check ceres.log).")))
                    .binding(false, cfg::isDebugMode, cfg::setDebugMode)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Bypass Area Check"))
                    .description(OptionDescription.of(Text.literal(
                        "Allow the bot to start outside expected areas. Useful for testing paths.")))
                    .binding(false, cfg::isBypassAreaCheck, cfg::setBypassAreaCheck)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Boolean>createBuilder()
                    .name(Text.literal("Micro-Look Jitter"))
                    .description(OptionDescription.of(Text.literal(
                        "Enable subtle random camera movements (HumanProfile). Off by default.")))
                    .binding(false, cfg::isMicroLookEnabled, cfg::setMicroLookEnabled)
                    .controller(BooleanControllerBuilder::create)
                    .build())

                .option(Option.<Integer>createBuilder()
                    .name(Text.literal("Log Level"))
                    .description(OptionDescription.of(Text.literal(
                        "DEBUG = most verbose. ERROR = errors only.")))
                    .binding(BotLogger.LEVEL_WARN, cfg::getLogLevel, cfg::setLogLevel)
                    .controller(opt -> CyclingListControllerBuilder.create(opt)
                        .values(List.of(
                            BotLogger.LEVEL_DEBUG,
                            BotLogger.LEVEL_INFO,
                            BotLogger.LEVEL_WARN,
                            BotLogger.LEVEL_ERROR))
                        .valueFormatter(v -> switch (v) {
                            case BotLogger.LEVEL_DEBUG -> Text.literal("DEBUG");
                            case BotLogger.LEVEL_INFO  -> Text.literal("INFO");
                            case BotLogger.LEVEL_WARN  -> Text.literal("WARN");
                            default                    -> Text.literal("ERROR");
                        }))
                    .build())

                .build())

            .save(cfg::save)
            .build()
            .generateScreen(parent);
    }

    // ── HUD line option helper ────────────────────────────────────────────────

    private static Option<Boolean> hudLineOption(String key, String label, String description, BotConfig cfg) {
        return Option.<Boolean>createBuilder()
            .name(Text.literal(label))
            .description(OptionDescription.of(Text.literal(description)))
            .binding(true, () -> cfg.isHudLineVisible(key), v -> cfg.setHudLineVisible(key, v))
            .controller(BooleanControllerBuilder::create)
            .build();
    }

    // ── Auto-load crop option helper ──────────────────────────────────────────

    private static Option<Boolean> autoLoadCropOption(String cropName, BotConfig cfg) {
        return Option.<Boolean>createBuilder()
            .name(Text.literal(cropName))
            .description(OptionDescription.of(Text.literal(
                "When enabled, holding the matching tool will auto-load the \"" + cropName +
                "\" profile on bot start.\nRequires the \"Enable Auto-Load\" master toggle to be on.")))
            .binding(true, () -> cfg.isAutoLoadCropEnabled(cropName),
                          v  -> cfg.setAutoLoadCropEnabled(cropName, v))
            .controller(BooleanControllerBuilder::create)
            .build();
    }

    // ── AlarmSound option helpers ─────────────────────────────────────────────

    private static Option<String> alarmSoundIdOption(String name, String defaultId,
                                                      AlarmSound sound, BotConfig cfg) {
        return Option.<String>createBuilder()
            .name(Text.literal(name))
            .description(OptionDescription.of(Text.literal(
                "Minecraft sound ID. Omit the 'minecraft:' prefix for vanilla sounds.\n" +
                "Example: entity.player.levelup\n" +
                "Browse all sounds at: https://misode.github.io/sounds/")))
            .binding(defaultId, () -> sound.soundId, v -> { sound.soundId = v; cfg.save(); })
            .controller(StringControllerBuilder::create)
            .build();
    }

    private static Option<Double> alarmVolumeOption(String name, AlarmSound sound, BotConfig cfg) {
        return Option.<Double>createBuilder()
            .name(Text.literal(name))
            .description(OptionDescription.of(Text.literal("Volume: 0.1 = very quiet, 1.0 = normal, 2.0 = loud.")))
            .binding(1.0, () -> sound.volume, v -> { sound.volume = v; cfg.save(); })
            .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 2.0).step(0.05))
            .build();
    }

    private static Option<Double> alarmPitchOption(String name, AlarmSound sound, BotConfig cfg) {
        return Option.<Double>createBuilder()
            .name(Text.literal(name))
            .description(OptionDescription.of(Text.literal(
                "Pitch: 0.5 = low and slow, 1.0 = normal, 2.0 = high and fast.")))
            .binding(1.0, () -> sound.pitch, v -> { sound.pitch = v; cfg.save(); })
            .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.5, 2.0).step(0.05))
            .build();
    }

    private static Option<Integer> alarmRepeatOption(String name, AlarmSound sound, BotConfig cfg) {
        return Option.<Integer>createBuilder()
            .name(Text.literal(name))
            .description(OptionDescription.of(Text.literal(
                "How long the alarm plays in total. Set to 0 to play once with no repeats.")))
            .binding(10, () -> sound.durationSeconds, v -> { sound.durationSeconds = v; cfg.save(); })
            .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 30).step(1)
                .valueFormatter(v -> Text.literal(v + "s")))
            .build();
    }

    private static Option<Integer> alarmIntervalOption(String name, AlarmSound sound, BotConfig cfg) {
        return Option.<Integer>createBuilder()
            .name(Text.literal(name))
            .description(OptionDescription.of(Text.literal(
                "Ticks between each repeat. 20 ticks = 1 second.")))
            .binding(20, () -> sound.intervalTicks, v -> { sound.intervalTicks = v; cfg.save(); })
            .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(5, 60).step(5))
            .build();
    }
}

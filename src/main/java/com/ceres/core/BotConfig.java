package com.ceres.core;

import com.ceres.path.CropToolMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.playerapi.HumanProfile;
import com.playerapi.SoundActions;
import com.playerapi.config.annotations.Button;
import com.playerapi.config.annotations.ConfigAccordion;
import com.playerapi.config.annotations.ConfigLayout;
import com.playerapi.config.annotations.ConfigMapToggles;
import com.playerapi.config.annotations.ConfigOption;
import com.playerapi.config.annotations.Dropdown;
import com.playerapi.config.annotations.OnChange;
import com.playerapi.config.annotations.ShowIf;
import com.playerapi.config.annotations.Slider;
import com.playerapi.config.annotations.TextField;
import com.playerapi.config.annotations.Toggle;
import com.playerapi.config.theme.ConfigStyle;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigLayout({
        @ConfigLayout.Category(name = "Bot Settings"),
        @ConfigLayout.Category(name = "Checkers"),
        @ConfigLayout.Category(name = "Auto-Load"),
        @ConfigLayout.Category(name = "HUD"),
        @ConfigLayout.Category(name = "Sounds"),
        @ConfigLayout.Category(name = "Updates"),
        @ConfigLayout.Category(name = "Developer", color = 0xFFFF5555),
})
public class BotConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("ceres/config.json");

    /**
     * Increment this whenever a field is renamed, removed, or its meaning changes.
     * Add a corresponding migrateVn() method in the migration section below.
     *
     * Version history:
     *   0 — initial release (no version field present in file)
     *   1 — alarm sound repeatCount (int, times) → durationSeconds (int, seconds)
     *   2 — added autoLoadEnabled + autoLoadCrops (all default true)
     *   3 — added hudLines per-row visibility map (all default true)
     *   4 — added updateCheckEnabled (default true)
     *   5 — added rebootAlertEnabled + rebootAlertSound (default true / bell)
     *   6 — added hudX / hudY / hudScale (shared HUD editor layout)
     *   7 — added keybindHintsVisible + hints HUD element layout (hintsHudX/Y/Scale, hintsPositioned)
     */
    private static final int CURRENT_VERSION = 10;

    /** All toggleable HUD row keys, in display order. */
    public static final List<String> ALL_HUD_LINES = List.of(
            "profile", "area", "xyz", "look", "pests",
            "plots", "spray", "repellent", "bonus", "cooldown", "pest_chance",
            "path", "bps", "target"
    );

    // INSTANCE must be declared AFTER ALL_HUD_LINES — the BotConfig() constructor
    // iterates that list, so it must be non-null when <clinit> reaches this line.
    private static final BotConfig INSTANCE = new BotConfig();

    // Stored in the file so load() knows what migrations to run.
    private int configVersion = CURRENT_VERSION;

    // ── Bot settings ──────────────────────────────────────────────────────────
    @ConfigOption(category = "Checkers", name = "Min Pest Count for Alarm", desc = "Alert when pests reach this count.")
    @Slider(min = 1, max = 8, step = 1)
    private int minPestCount = 4;
    @ConfigOption(category = "Developer", name = "Log Level", desc = "How much detail the bot logs (console + file).")
    @Dropdown
    @OnChange("onLogLevelChanged")
    private LogLevel logLevel = LogLevel.WARN;

    /**
     * Log verbosity, shown as a labelled dropdown. Each constant maps to the matching {@link BotLogger}
     * level, so selecting a name always sets the correct level (the old int slider was mismatched with the
     * logger's constants). Ordered least→most verbose.
     */
    public enum LogLevel {
        ERROR("Error", BotLogger.LEVEL_ERROR),
        WARN ("Warn",  BotLogger.LEVEL_WARN),
        INFO ("Info",  BotLogger.LEVEL_INFO),
        DEBUG("Debug", BotLogger.LEVEL_DEBUG);
        public final String label;
        public final int level;
        LogLevel(String label, int level) { this.label = label; this.level = level; }
        @Override public String toString() { return label; }
        /** Map a raw {@link BotLogger} level int back to the enum (defaults to WARN if none match). */
        public static LogLevel fromLevel(int lv) {
            for (LogLevel e : values()) if (e.level == lv) return e;
            return WARN;
        }
    }
    @ConfigOption(category = "Bot Settings", name = "Sneak on Path Start", desc = "Hold sneak briefly when a path begins.")
    @Toggle
    private boolean sneakOnPathStart = true;
    @ConfigOption(category = "Bot Settings", name = "Pest Repellent Reapply", desc = "Auto-use Pest Repellent when it expires.")
    @Toggle
    private boolean repellentReapplyEnabled = true;

    // ── Checker toggles ───────────────────────────────────────────────────────
    @ConfigOption(category = "Checkers", name = "Inventory Checker", desc = "Alert if the inventory stops changing.")
    @Toggle
    private boolean inventoryCheckerEnabled = true;
    @ConfigOption(category = "Checkers", name = "Tool Checker", desc = "Alert if the held tool changes.")
    @Toggle
    private boolean toolCheckerEnabled = true;
    @ConfigOption(category = "Checkers", name = "Yaw / Pitch Checker", desc = "Alert on unexpected look changes.")
    @Toggle
    private boolean yawPitchCheckerEnabled = true;
    @ConfigOption(category = "Checkers", name = "Pest Checker", desc = "Stop when pests reach the threshold.")
    @Toggle
    private boolean pestCheckerEnabled = true;
    @ConfigOption(category = "Checkers", name = "Movement Checker", desc = "Stop if the player stops moving.")
    @Toggle
    private boolean movementCheckerEnabled = true;
    @ConfigOption(category = "Checkers", name = "Crop Check (on start)", desc = "Warn if the crop under you doesn't match the tool.")
    @Toggle
    private boolean cropCheckEnabled = true;

    // ── Cycle mode ────────────────────────────────────────────────────────────
    @ConfigOption(category = "Bot Settings", name = "One Cycle Mode", desc = "Stop (or restart) after a single path cycle.")
    @Toggle
    private boolean oneCycleMode = false;
    @ConfigOption(category = "Bot Settings", name = "Cycle Start Command", desc = "Command run to restart a cycle.")
    @TextField
    @ShowIf("oneCycleMode")
    private String cycleRestartCommand = "warp garden";

    // ── Alarm sounds ──────────────────────────────────────────────────────────
    @ConfigOption(category = "Sounds", name = "Cycle Complete", desc = "Plays when a cycle finishes.")
    @ConfigAccordion(expanded = false)
    private AlarmSound cycleCompleteSound = AlarmSound.defaultStop();
    @ConfigOption(category = "Sounds", name = "Stop Alert", desc = "Plays when a checker stops the bot.")
    @ConfigAccordion(expanded = false)
    private AlarmSound stopAlertSound     = AlarmSound.defaultStop();
    @ConfigOption(category = "Sounds", name = "Warn Alert", desc = "Plays on a non-stopping warning.")
    @ConfigAccordion(expanded = false)
    private AlarmSound warnAlertSound     = AlarmSound.defaultWarn();
    @ConfigOption(category = "Sounds", name = "Reboot Alert", desc = "Loops on the server-reboot warning.")
    @ConfigAccordion(expanded = false)
    private AlarmSound rebootAlertSound   = AlarmSound.defaultReboot();

    // ── Auto-load ─────────────────────────────────────────────────────────────
    @ConfigOption(category = "Auto-Load", name = "Enable Auto-Load", desc = "Load the profile matching your held tool on start.")
    @Toggle
    private boolean autoLoadEnabled = true;
    /** Per-crop enable flags. Keys are profile names from CropToolMapper.ALL_CROPS. */
    @ConfigMapToggles(category = "Auto-Load", subcategory = "Crops")
    private Map<String, Boolean> autoLoadCrops = new LinkedHashMap<>();

    // ── HUD line visibility ────────────────────────────────────────────────────
    /** Per-row visibility flags. Keys are entries from ALL_HUD_LINES. */
    @ConfigMapToggles(category = "HUD", subcategory = "Lines")
    private Map<String, Boolean> hudLines = new LinkedHashMap<>();

    // ── HUD panel buttons/toggles ───────────────────────────────────────────────
    @ConfigOption(category = "HUD", name = "Edit HUD Position", desc = "Move/scale the HUD panels.")
    @Button(text = "Open HUD editor")
    public transient final Runnable editHudAction = () -> com.ceres.gui.BotHudRenderer.openEditor();

    // ── HUD layout (position + scale, edited via the shared HUD editor) ─────────
    /** Top-left screen position + uniform scale of the Ceres HUD panel. */
    private float hudX     = 4f;
    private float hudY     = 4f;
    private float hudScale = 1.0f;

    // ── Keybind hints panel — REMOVED from the HUD (2026-08-16). Fields kept only so old
    //    config.json files still load cleanly; no longer shown, rendered, or editable. ──────────
    private boolean keybindHintsVisible = true;
    /** Unused (legacy layout of the removed hints panel). */
    private float   hintsHudX      = 0f;
    private float   hintsHudY      = 0f;
    private float   hintsHudScale  = 1.0f;
    /** Set true once the user has dragged the hints panel; before that it tracks the screen corner. */
    private boolean hintsPositioned = false;

    // ── Log panel (its own movable/scalable HUD element) ────────────────────────
    /** Whether the log panel is shown at all. */
    @ConfigOption(category = "HUD", name = "Show Log", desc = "Show the scrolling log panel.")
    @Toggle
    private boolean logVisible  = true;
    private float   logHudX     = 4f;
    private float   logHudY     = 150f;
    private float   logHudScale = 1.0f;

    // ── Update checker ────────────────────────────────────────────────────────
    @ConfigOption(category = "Updates", name = "Update Check", desc = "On world join, check GitHub for a newer release.")
    @Toggle
    private boolean updateCheckEnabled = true;

    // ── Reboot alert ──────────────────────────────────────────────────────────
    @ConfigOption(category = "Sounds", name = "Enable Reboot Alert", desc = "Play the reboot alarm on the server-restart warning.")
    @Toggle
    private boolean rebootAlertEnabled = true;

    // ── HUD style ───────────────────────────────────────────────────────────────
    @ConfigOption(category = "HUD", name = "HUD Style",
            desc = "Live HUD look: Custom textured panels, a toned-down transparent look, or plain flat colours.")
    @Dropdown
    private ConfigStyle hudStyle = ConfigStyle.CUSTOM;

    // ── Developer ─────────────────────────────────────────────────────────────
    @ConfigOption(category = "Developer", name = "Bypass Area Check", desc = "Skip the Garden-area safety gate.")
    @Toggle
    private boolean bypassAreaCheck = false;
    @ConfigOption(category = "Developer", name = "Micro Look", desc = "Tiny idle look movements (anti-AFK experiment).")
    @Toggle
    private boolean microLookEnabled = false;
    @ConfigOption(category = "Developer", name = "Debug Mode", desc = "Extra diagnostics.")
    @Toggle
    private boolean debugMode = false;

    private BotConfig() {
        // Pre-populate per-crop map so GSON always has something to merge into
        for (String crop : CropToolMapper.ALL_CROPS) {
            autoLoadCrops.put(crop, true);
        }
        // Pre-populate HUD line map so GSON always has something to merge into
        for (String key : ALL_HUD_LINES) {
            hudLines.put(key, true);
        }
    }

    public static BotConfig getInstance() {
        return INSTANCE;
    }

    /** Chosen live-HUD style (never null). */
    public ConfigStyle getHudStyle() {
        return hudStyle == null ? ConfigStyle.CUSTOM : hudStyle;
    }


    // ── AlarmSound data class ─────────────────────────────────────────────────

    /**
     * Configuration for a single alarm sound event.
     * Sound IDs use the Minecraft resource format: "namespace:sound.id"
     * Browse all vanilla sounds at: https://misode.github.io/sounds/
     * Example: "minecraft:entity.player.levelup"
     */
    public static class AlarmSound {
        @ConfigOption(name = "Sound ID", desc = "e.g. minecraft:entity.player.levelup")
        @TextField
        public String soundId;
        @ConfigOption(name = "Volume", desc = "0.1 – 2.0")
        @Slider(min = 0.1, max = 2.0, step = 0.1)
        public double volume;          // 0.1 – 2.0
        @ConfigOption(name = "Pitch", desc = "0.5 – 2.0 (lower = deeper)")
        @Slider(min = 0.5, max = 2.0, step = 0.1)
        public double pitch;           // 0.5 – 2.0 (lower = slower and deeper)
        @ConfigOption(name = "Duration (s)", desc = "Total seconds the alarm plays (0 = once)")
        @Slider(min = 0, max = 30, step = 1)
        public int    durationSeconds; // 0 – 30 seconds total alarm duration
        @ConfigOption(name = "Interval (ticks)", desc = "Ticks between plays")
        @Slider(min = 5, max = 60, step = 5)
        public int    intervalTicks;   // 5 – 60 ticks between each play

        public AlarmSound() {}

        public AlarmSound(String soundId, double volume, double pitch, int durationSeconds, int intervalTicks) {
            this.soundId         = soundId;
            this.volume          = volume;
            this.pitch           = pitch;
            this.durationSeconds = durationSeconds;
            this.intervalTicks   = intervalTicks;
        }

        /** Default for stop-type alerts (cycle complete, checker stops). */
        public static AlarmSound defaultStop() {
            return new AlarmSound("minecraft:entity.player.levelup", 1.0, 1.0, 10, 20);
        }

        /** Default for warning-type alerts (yaw/pitch, pest count). */
        public static AlarmSound defaultWarn() {
            return new AlarmSound("minecraft:entity.experience_orb.pickup", 1.0, 1.5, 5, 15);
        }

        /**
         * Default for the reboot alert — plays on a 2-second loop until the
         * player leaves the Garden. durationSeconds is unused for this alarm.
         */
        public static AlarmSound defaultReboot() {
            return new AlarmSound("minecraft:block.bell.use", 1.0, 1.0, 0, 40);
        }

        /** Copy values from a loaded instance, keeping defaults for any invalid/missing fields. */
        public void mergeFrom(AlarmSound src, AlarmSound defaults) {
            soundId         = (src.soundId != null && !src.soundId.isBlank()) ? src.soundId : defaults.soundId;
            volume          = src.volume > 0          ? src.volume          : defaults.volume;
            pitch           = src.pitch > 0           ? src.pitch           : defaults.pitch;
            durationSeconds = src.durationSeconds > 0 ? src.durationSeconds : defaults.durationSeconds;
            intervalTicks   = src.intervalTicks > 0   ? src.intervalTicks   : defaults.intervalTicks;
        }

        /** Play this alarm sound, repeating for the configured duration. */
        public void play() {
            if (soundId == null || soundId.isBlank() || durationSeconds <= 0) return;
            int totalTicks = durationSeconds * 20;
            int times = Math.max(1, totalTicks / Math.max(1, intervalTicks));
            SoundActions.playByIdRepeated(soundId, (float) volume, (float) pitch, times, intervalTicks);
        }
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            applyRuntimeFields();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                json = migrate(json);
                BotConfig loaded = GSON.fromJson(json, BotConfig.class);
                if (loaded != null) {
                    this.configVersion             = CURRENT_VERSION;
                    this.minPestCount              = Math.max(1, Math.min(8, loaded.minPestCount));
                    this.logLevel                  = loaded.logLevel != null ? loaded.logLevel : LogLevel.WARN;
                    this.sneakOnPathStart          = loaded.sneakOnPathStart;
                    this.repellentReapplyEnabled   = loaded.repellentReapplyEnabled;
                    this.inventoryCheckerEnabled   = loaded.inventoryCheckerEnabled;
                    this.toolCheckerEnabled        = loaded.toolCheckerEnabled;
                    this.yawPitchCheckerEnabled    = loaded.yawPitchCheckerEnabled;
                    this.pestCheckerEnabled        = loaded.pestCheckerEnabled;
                    this.movementCheckerEnabled    = loaded.movementCheckerEnabled;
                    this.cropCheckEnabled          = loaded.cropCheckEnabled;
                    this.oneCycleMode              = loaded.oneCycleMode;
                    this.cycleRestartCommand       = loaded.cycleRestartCommand != null ? loaded.cycleRestartCommand : "";
                    this.autoLoadEnabled           = loaded.autoLoadEnabled;
                    if (loaded.autoLoadCrops != null) {
                        // Merge: keep default true for any crop not present in the file
                        for (String crop : CropToolMapper.ALL_CROPS) {
                            this.autoLoadCrops.put(crop,
                                loaded.autoLoadCrops.getOrDefault(crop, true));
                        }
                    }
                    if (loaded.hudLines != null) {
                        // Merge: keep default true for any key not present in the file
                        for (String key : ALL_HUD_LINES) {
                            this.hudLines.put(key,
                                loaded.hudLines.getOrDefault(key, true));
                        }
                    }
                    this.hudX                      = loaded.hudX;
                    this.hudY                      = loaded.hudY;
                    this.hudScale                  = loaded.hudScale > 0 ? loaded.hudScale : 1.0f;
                    this.keybindHintsVisible       = loaded.keybindHintsVisible;
                    this.hintsHudX                 = loaded.hintsHudX;
                    this.hintsHudY                 = loaded.hintsHudY;
                    this.hintsHudScale             = loaded.hintsHudScale > 0 ? loaded.hintsHudScale : 1.0f;
                    this.hintsPositioned           = loaded.hintsPositioned;
                    this.logVisible                = loaded.logVisible;
                    this.logHudX                   = loaded.logHudX;
                    this.logHudY                   = loaded.logHudY;
                    this.logHudScale               = loaded.logHudScale > 0 ? loaded.logHudScale : 1.0f;
                    this.updateCheckEnabled        = loaded.updateCheckEnabled;
                    this.rebootAlertEnabled        = loaded.rebootAlertEnabled;
                    this.bypassAreaCheck           = loaded.bypassAreaCheck;
                    this.microLookEnabled          = loaded.microLookEnabled;
                    this.debugMode                 = loaded.debugMode;
                    this.hudStyle                  = loaded.hudStyle != null ? loaded.hudStyle : ConfigStyle.CUSTOM;

                    if (loaded.cycleCompleteSound != null)
                        this.cycleCompleteSound.mergeFrom(loaded.cycleCompleteSound, AlarmSound.defaultStop());
                    if (loaded.stopAlertSound != null)
                        this.stopAlertSound.mergeFrom(loaded.stopAlertSound, AlarmSound.defaultStop());
                    if (loaded.warnAlertSound != null)
                        this.warnAlertSound.mergeFrom(loaded.warnAlertSound, AlarmSound.defaultWarn());
                    if (loaded.rebootAlertSound != null)
                        this.rebootAlertSound.mergeFrom(loaded.rebootAlertSound, AlarmSound.defaultReboot());
                }
            }
        } catch (Exception e) {
            BotLogger.getInstance().logError("BotConfig: Failed to load: " + e.getMessage());
        }
        applyRuntimeFields();
        save(); // persist any migrations that ran
    }

    private void applyRuntimeFields() {
        BotLogger.getInstance().setLogLevel(logLevel.level);
        HumanProfile.getInstance().enableMicroLook = microLookEnabled;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            BotLogger.getInstance().logError("BotConfig: Failed to save: " + e.getMessage());
        }
    }

    // ── Migration ─────────────────────────────────────────────────────────────
    //
    // To add a migration for a future breaking change:
    //   1. Increment CURRENT_VERSION
    //   2. Add a private static JsonObject migrateVn(JsonObject json) method
    //   3. Call it in migrate() with the appropriate version check
    //
    // Each step receives the JsonObject as it currently stands and returns it
    // modified. Steps run in order so every old version is brought forward
    // one step at a time.

    private static JsonObject migrate(JsonObject json) {
        int version = json.has("configVersion") ? json.get("configVersion").getAsInt() : 0;

        if (version < 1) json = migrateV0toV1(json);
        if (version < 2) json = migrateV1toV2(json);
        if (version < 3) json = migrateV2toV3(json);
        if (version < 4) json = migrateV3toV4(json);
        if (version < 5) json = migrateV4toV5(json);
        if (version < 6) json = migrateV5toV6(json);
        if (version < 7) json = migrateV6toV7(json);
        if (version < 8) json = migrateV7toV8(json);
        if (version < 9) json = migrateV8toV9(json);
        if (version < 10) json = migrateV9toV10(json);

        json.addProperty("configVersion", CURRENT_VERSION);
        BotLogger.getInstance().logInfo("BotConfig: loaded (schema v" + version
                + (version < CURRENT_VERSION ? " → migrated to v" + CURRENT_VERSION : "") + ")");
        return json;
    }

    /**
     * v9 → v10: {@code logLevel} changed from an int slider (whose 0–3 range never matched BotLogger's
     * 1–4 constants) to a {@code LogLevel} enum. Map the old numeric value through the logger constants to
     * the enum name so it deserializes correctly; anything that doesn't map falls back to WARN.
     */
    private static JsonObject migrateV9toV10(JsonObject json) {
        if (json.has("logLevel") && json.get("logLevel").isJsonPrimitive()
                && json.get("logLevel").getAsJsonPrimitive().isNumber()) {
            json.addProperty("logLevel", LogLevel.fromLevel(json.get("logLevel").getAsInt()).name());
        }
        return json;
    }

    /**
     * v0 → v1: alarm sound {@code repeatCount} (number of plays) was replaced by
     * {@code durationSeconds} (total alarm duration in seconds).
     * Conversion: durationSeconds ≈ repeatCount × intervalTicks / 20.
     */
    private static JsonObject migrateV0toV1(JsonObject json) {
        for (String key : new String[]{"cycleCompleteSound", "stopAlertSound", "warnAlertSound"}) {
            if (!json.has(key)) continue;
            JsonObject sound = json.getAsJsonObject(key);
            if (!sound.has("repeatCount")) continue;

            int repeatCount   = sound.get("repeatCount").getAsInt();
            int intervalTicks = sound.has("intervalTicks") ? sound.get("intervalTicks").getAsInt() : 20;
            int durationSecs  = Math.max(1, (repeatCount * intervalTicks) / 20);

            sound.addProperty("durationSeconds", durationSecs);
            sound.remove("repeatCount");
        }
        return json;
    }

    /**
     * v1 → v2: added autoLoadEnabled (default true) and autoLoadCrops (all default true).
     * Old config files won't have these keys, so GSON would default booleans to false.
     * Inject the correct defaults into the JSON before GSON parses it.
     */
    private static JsonObject migrateV1toV2(JsonObject json) {
        if (!json.has("autoLoadEnabled")) {
            json.addProperty("autoLoadEnabled", true);
        }
        if (!json.has("autoLoadCrops")) {
            JsonObject crops = new JsonObject();
            for (String crop : CropToolMapper.ALL_CROPS) {
                crops.addProperty(crop, true);
            }
            json.add("autoLoadCrops", crops);
        }
        return json;
    }

    /**
     * v2 → v3: added hudLines map (all rows default to visible/true).
     * Old config files won't have this key, so GSON would default booleans to false.
     */
    private static JsonObject migrateV2toV3(JsonObject json) {
        if (!json.has("hudLines")) {
            JsonObject lines = new JsonObject();
            for (String key : ALL_HUD_LINES) {
                lines.addProperty(key, true);
            }
            json.add("hudLines", lines);
        }
        return json;
    }

    /**
     * v3 → v4: added updateCheckEnabled (default true).
     * GSON defaults missing booleans to false, so inject the correct default here.
     */
    private static JsonObject migrateV3toV4(JsonObject json) {
        if (!json.has("updateCheckEnabled")) {
            json.addProperty("updateCheckEnabled", true);
        }
        return json;
    }

    /**
     * v4 → v5: added rebootAlertEnabled (default true).
     * GSON defaults missing booleans to false, so inject the correct default here.
     */
    private static JsonObject migrateV4toV5(JsonObject json) {
        if (!json.has("rebootAlertEnabled")) {
            json.addProperty("rebootAlertEnabled", true);
        }
        return json;
    }

    /**
     * v5 → v6: added HUD layout fields. Inject the current default position/scale so an upgraded
     * config keeps the panel where it was (top-left, 1.0x) — and, critically, so hudScale is never
     * left as GSON's 0.0 (which would render the panel invisibly small).
     */
    private static JsonObject migrateV5toV6(JsonObject json) {
        if (!json.has("hudX"))     json.addProperty("hudX", 4.0f);
        if (!json.has("hudY"))     json.addProperty("hudY", 4.0f);
        if (!json.has("hudScale")) json.addProperty("hudScale", 1.0f);
        return json;
    }

    /**
     * v6 → v7: added the keybind-hints toggle + its own HUD element layout. hintsPositioned defaults
     * false (so the panel keeps auto-anchoring bottom-right until the user drags it); hintsHudScale
     * must be injected as 1.0 so it is never GSON's 0.0.
     */
    private static JsonObject migrateV6toV7(JsonObject json) {
        if (!json.has("keybindHintsVisible")) json.addProperty("keybindHintsVisible", true);
        if (!json.has("hintsHudScale"))       json.addProperty("hintsHudScale", 1.0f);
        return json;
    }

    private static JsonObject migrateV7toV8(JsonObject json) {
        if (!json.has("movementCheckerEnabled")) json.addProperty("movementCheckerEnabled", true);
        if (!json.has("cropCheckEnabled"))       json.addProperty("cropCheckEnabled", true);
        return json;
    }

    private static JsonObject migrateV8toV9(JsonObject json) {
        if (!json.has("logVisible"))  json.addProperty("logVisible", true);
        if (!json.has("logHudScale")) json.addProperty("logHudScale", 1.0f);
        if (!json.has("logHudY"))     json.addProperty("logHudY", 150f);
        return json;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getMinPestCount() { return minPestCount; }
    // A plot maxes at 8 pests; alerting at 0 is pointless — clamp to 1–8.
    public void setMinPestCount(int v) { minPestCount = Math.max(1, Math.min(8, v)); save(); }

    public int getLogLevel() { return logLevel.level; }
    /** {@code @OnChange} hook: the config screen edits the field directly, so re-apply to the logger. */
    public void onLogLevelChanged() {
        BotLogger.getInstance().setLogLevel(logLevel.level);
    }

    public void setLogLevel(int v) {
        logLevel = LogLevel.fromLevel(v);
        BotLogger.getInstance().setLogLevel(logLevel.level);
        save();
    }

    public boolean isSneakOnPathStart() { return sneakOnPathStart; }
    public void setSneakOnPathStart(boolean v) { sneakOnPathStart = v; save(); }

    public boolean isRepellentReapplyEnabled() { return repellentReapplyEnabled; }
    public void setRepellentReapplyEnabled(boolean v) { repellentReapplyEnabled = v; save(); }

    public boolean isInventoryCheckerEnabled() { return inventoryCheckerEnabled; }
    public void setInventoryCheckerEnabled(boolean v) { inventoryCheckerEnabled = v; save(); }

    public boolean isToolCheckerEnabled() { return toolCheckerEnabled; }
    public void setToolCheckerEnabled(boolean v) { toolCheckerEnabled = v; save(); }

    public boolean isYawPitchCheckerEnabled() { return yawPitchCheckerEnabled; }
    public void setYawPitchCheckerEnabled(boolean v) { yawPitchCheckerEnabled = v; save(); }

    public boolean isPestCheckerEnabled() { return pestCheckerEnabled; }
    public void setPestCheckerEnabled(boolean v) { pestCheckerEnabled = v; save(); }

    public boolean isMovementCheckerEnabled() { return movementCheckerEnabled; }
    public void setMovementCheckerEnabled(boolean v) { movementCheckerEnabled = v; save(); }

    public boolean isCropCheckEnabled() { return cropCheckEnabled; }
    public void setCropCheckEnabled(boolean v) { cropCheckEnabled = v; save(); }

    public boolean isOneCycleMode() { return oneCycleMode; }
    public void setOneCycleMode(boolean v) { oneCycleMode = v; save(); }

    public String getCycleRestartCommand() { return cycleRestartCommand; }
    public void setCycleRestartCommand(String v) { cycleRestartCommand = v != null ? v : ""; save(); }

    public AlarmSound getCycleCompleteSound() { return cycleCompleteSound; }
    public AlarmSound getStopAlertSound()     { return stopAlertSound; }
    public AlarmSound getWarnAlertSound()     { return warnAlertSound; }

    public boolean isBypassAreaCheck() { return bypassAreaCheck; }
    public void setBypassAreaCheck(boolean v) { bypassAreaCheck = v; save(); }

    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean v) { debugMode = v; save(); }

    public boolean isMicroLookEnabled() { return microLookEnabled; }
    public void setMicroLookEnabled(boolean v) {
        microLookEnabled = v;
        HumanProfile.getInstance().enableMicroLook = v;
        save();
    }

    public float getHudX()     { return hudX; }
    public float getHudY()     { return hudY; }
    public float getHudScale() { return hudScale; }

    /** Persists the HUD panel's position + scale in one write (called when the editor closes). */
    public void setHudLayout(float x, float y, float scale) {
        this.hudX = x;
        this.hudY = y;
        this.hudScale = scale > 0 ? scale : 1.0f;
        save();
    }

    public boolean isKeybindHintsVisible() { return keybindHintsVisible; }
    public void setKeybindHintsVisible(boolean v) { keybindHintsVisible = v; save(); }

    public float getHintsHudX()     { return hintsHudX; }
    public float getHintsHudY()     { return hintsHudY; }
    public float getHintsHudScale() { return hintsHudScale; }
    public boolean isHintsPositioned() { return hintsPositioned; }

    /** Persists the hints panel's position + scale, and marks it as user-positioned (stops auto-anchoring). */
    public void setHintsLayout(float x, float y, float scale) {
        this.hintsHudX = x;
        this.hintsHudY = y;
        this.hintsHudScale = scale > 0 ? scale : 1.0f;
        this.hintsPositioned = true;
        save();
    }

    public boolean isLogVisible() { return logVisible; }
    public void setLogVisible(boolean v) { logVisible = v; save(); }

    public float getLogHudX()     { return logHudX; }
    public float getLogHudY()     { return logHudY; }
    public float getLogHudScale() { return logHudScale; }

    /** Persists the log panel's position + scale (called when the editor closes). */
    public void setLogLayout(float x, float y, float scale) {
        this.logHudX = x;
        this.logHudY = y;
        this.logHudScale = scale > 0 ? scale : 1.0f;
        save();
    }

    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    public void setUpdateCheckEnabled(boolean v) { updateCheckEnabled = v; save(); }

    public boolean isRebootAlertEnabled() { return rebootAlertEnabled; }
    public void setRebootAlertEnabled(boolean v) { rebootAlertEnabled = v; save(); }

    public AlarmSound getRebootAlertSound() { return rebootAlertSound; }

    public boolean isAutoLoadEnabled() { return autoLoadEnabled; }
    public void setAutoLoadEnabled(boolean v) { autoLoadEnabled = v; save(); }

    /** Returns whether auto-load is enabled for a specific crop profile name. Defaults to true. */
    public boolean isAutoLoadCropEnabled(String cropName) {
        return autoLoadCrops.getOrDefault(cropName, true);
    }
    public void setAutoLoadCropEnabled(String cropName, boolean v) {
        autoLoadCrops.put(cropName, v);
        save();
    }

    /** Returns whether the named HUD row should be rendered. Defaults to true. */
    public boolean isHudLineVisible(String key) {
        return hudLines.getOrDefault(key, true);
    }
    public void setHudLineVisible(String key, boolean v) {
        hudLines.put(key, v);
        save();
    }
}

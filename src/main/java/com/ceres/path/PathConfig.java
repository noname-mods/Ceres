package com.ceres.path;

import com.ceres.core.BotLogger;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PathConfig {

    private static final PathConfig INSTANCE = new PathConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("ceres/paths.json");

    private final Map<PathType, List<Waypoint>> paths = new EnumMap<>(PathType.class);
    private final Map<PathType, Boolean> sprintEnabled = new EnumMap<>(PathType.class);

    private PathConfig() {
        for (PathType t : PathType.values()) {
            paths.put(t, new ArrayList<>());
            sprintEnabled.put(t, true);
        }
    }

    public static PathConfig getInstance() { return INSTANCE; }

    public void load() {
        if (!Files.exists(CONFIG_FILE)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            JsonElement sprintEl = root.get("sprint");
            if (sprintEl != null && sprintEl.isJsonObject()) {
                JsonObject sprintObj = sprintEl.getAsJsonObject();
                for (PathType t : PathType.values()) {
                    JsonElement el = sprintObj.get(t.name());
                    if (el != null) sprintEnabled.put(t, el.getAsBoolean());
                }
            }

            JsonObject pathsObj = root.getAsJsonObject("paths");
            if (pathsObj == null) return;
            Type listType = new TypeToken<List<Waypoint>>() {}.getType();
            for (PathType type : PathType.values()) {
                JsonElement el = pathsObj.get(type.name());
                if (el != null && el.isJsonArray()) {
                    List<Waypoint> waypoints = GSON.fromJson(el, listType);
                    if (waypoints != null) {
                        for (Waypoint w : waypoints) {
                            if (w.forcedKeys == null) w.forcedKeys = new ArrayList<>();
                        }
                        paths.put(type, waypoints);
                    }
                }
            }
            BotLogger.getInstance().logInfo("Paths loaded (" + CONFIG_FILE.getFileName() + ")");
        } catch (Exception e) {
            BotLogger.getInstance().logError("PathConfig load failed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);

            JsonObject sprintObj = new JsonObject();
            for (PathType t : PathType.values())
                sprintObj.addProperty(t.name(), sprintEnabled.getOrDefault(t, true));
            root.add("sprint", sprintObj);

            JsonObject pathsObj = new JsonObject();
            for (PathType type : PathType.values())
                pathsObj.add(type.name(), GSON.toJsonTree(paths.get(type)));
            root.add("paths", pathsObj);

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            BotLogger.getInstance().logError("PathConfig save failed: " + e.getMessage());
        }
    }

    public boolean isSprintEnabled(PathType type) {
        return sprintEnabled.getOrDefault(type, true);
    }

    public void setSprintEnabled(PathType type, boolean enabled) {
        sprintEnabled.put(type, enabled);
    }

    public Map<PathType, Boolean> getAllSprintSettings() {
        return new EnumMap<>(sprintEnabled);
    }

    public void setAllSprintSettings(Map<PathType, Boolean> settings) {
        for (PathType t : PathType.values()) {
            if (settings.containsKey(t)) sprintEnabled.put(t, settings.get(t));
        }
    }

    public List<Waypoint> getPathPoints(PathType type) {
        return Collections.unmodifiableList(paths.getOrDefault(type, Collections.emptyList()));
    }

    public void setPathPoints(PathType type, List<Waypoint> waypoints) {
        paths.put(type, new ArrayList<>(waypoints));
    }

    public Map<PathType, List<Waypoint>> getAllPaths() {
        Map<PathType, List<Waypoint>> copy = new EnumMap<>(PathType.class);
        for (PathType t : PathType.values())
            copy.put(t, new ArrayList<>(paths.get(t)));
        return copy;
    }

    public void setAllPaths(Map<PathType, List<Waypoint>> newPaths) {
        for (PathType t : PathType.values())
            paths.put(t, new ArrayList<>(newPaths.getOrDefault(t, Collections.emptyList())));
        save();
    }
}

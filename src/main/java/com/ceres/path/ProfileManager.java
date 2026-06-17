package com.ceres.path;

import com.ceres.core.BotLogger;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages named path profiles stored in config/ceres/profiles/<name>.json.
 * Profiles are separate from PathConfig: saving/loading a profile never
 * touches paths.json. The user commits to paths.json by pressing Done.
 */
public class ProfileManager {

    public record ProfileData(
            Map<PathType, List<Waypoint>> paths,
            Map<PathType, Boolean> sprint
    ) {}

    private static final ProfileManager INSTANCE = new ProfileManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROFILES_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("ceres/profiles");

    private ProfileManager() {}

    public static ProfileManager getInstance() { return INSTANCE; }

    private static Path ensureDir() throws Exception {
        Files.createDirectories(PROFILES_DIR);
        return PROFILES_DIR;
    }

    public static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static Path profileFile(String name) {
        return PROFILES_DIR.resolve(sanitize(name) + ".json");
    }

    public List<String> listProfiles() {
        try {
            ensureDir();
            try (Stream<Path> files = Files.list(PROFILES_DIR)) {
                return files
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> {
                            String fn = p.getFileName().toString();
                            return fn.substring(0, fn.length() - 5);
                        })
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        } catch (Exception e) {
            BotLogger.getInstance().logError("ProfileManager: listProfiles failed: " + e.getMessage());
            return List.of();
        }
    }

    public void saveProfile(String name,
                            Map<PathType, List<Waypoint>> paths,
                            Map<PathType, Boolean> sprint) {
        if (name == null || name.isBlank()) return;
        try {
            ensureDir();
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);

            JsonObject sprintObj = new JsonObject();
            for (PathType t : PathType.values())
                sprintObj.addProperty(t.name(), sprint.getOrDefault(t, true));
            root.add("sprint", sprintObj);

            JsonObject pathsObj = new JsonObject();
            for (PathType t : PathType.values())
                pathsObj.add(t.name(), GSON.toJsonTree(paths.getOrDefault(t, List.of())));
            root.add("paths", pathsObj);

            try (Writer w = Files.newBufferedWriter(profileFile(name))) {
                GSON.toJson(root, w);
            }
            BotLogger.getInstance().logInfo("Profile saved: " + name);
        } catch (Exception e) {
            BotLogger.getInstance().logError("ProfileManager: saveProfile failed: " + e.getMessage());
        }
    }

    public ProfileData loadProfile(String name) {
        Path file = profileFile(name);
        if (!Files.exists(file)) {
            BotLogger.getInstance().logWarn("ProfileManager: profile not found: " + name);
            return null;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

            Map<PathType, Boolean> sprint = new EnumMap<>(PathType.class);
            for (PathType t : PathType.values()) sprint.put(t, true);
            JsonElement sprintEl = root.get("sprint");
            if (sprintEl != null && sprintEl.isJsonObject()) {
                for (PathType t : PathType.values()) {
                    JsonElement el = sprintEl.getAsJsonObject().get(t.name());
                    if (el != null) sprint.put(t, el.getAsBoolean());
                }
            }

            Map<PathType, List<Waypoint>> paths = new EnumMap<>(PathType.class);
            for (PathType t : PathType.values()) paths.put(t, new ArrayList<>());
            Type listType = new TypeToken<List<Waypoint>>() {}.getType();
            JsonObject pathsObj = root.getAsJsonObject("paths");
            if (pathsObj != null) {
                for (PathType t : PathType.values()) {
                    JsonElement el = pathsObj.get(t.name());
                    if (el != null && el.isJsonArray()) {
                        List<Waypoint> wps = GSON.fromJson(el, listType);
                        if (wps != null) {
                            for (Waypoint w : wps)
                                if (w.forcedKeys == null) w.forcedKeys = new ArrayList<>();
                            paths.put(t, wps);
                        }
                    }
                }
            }
            BotLogger.getInstance().logInfo("Profile loaded: " + name);
            return new ProfileData(paths, sprint);
        } catch (Exception e) {
            BotLogger.getInstance().logError("ProfileManager: loadProfile failed: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteProfile(String name) {
        try {
            boolean deleted = Files.deleteIfExists(profileFile(name));
            if (deleted) BotLogger.getInstance().logInfo("Profile deleted: " + name);
            return deleted;
        } catch (Exception e) {
            BotLogger.getInstance().logError("ProfileManager: deleteProfile failed: " + e.getMessage());
            return false;
        }
    }

    public void openProfilesFolder() {
        try {
            ensureDir();
            Util.getPlatform().openPath(PROFILES_DIR);
        } catch (Exception e) {
            BotLogger.getInstance().logError("ProfileManager: openProfilesFolder failed: " + e.getMessage());
        }
    }
}

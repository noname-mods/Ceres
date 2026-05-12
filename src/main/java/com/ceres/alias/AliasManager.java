package com.ceres.alias;

import com.ceres.core.BotLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class AliasManager {

    private static final AliasManager INSTANCE = new AliasManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ALIAS_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("ceres/alias.json");

    private final Map<String, String> aliases = new LinkedHashMap<>();

    private AliasManager() {}

    public static AliasManager getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(ALIAS_FILE)) return;
        try (Reader reader = Files.newBufferedReader(ALIAS_FILE)) {
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) aliases.putAll(loaded);
            BotLogger.getInstance().logInfo("AliasManager: Loaded " + aliases.size() + " aliases");
        } catch (Exception e) {
            BotLogger.getInstance().logError("AliasManager: Load failed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(ALIAS_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(ALIAS_FILE)) {
                GSON.toJson(aliases, writer);
            }
        } catch (Exception e) {
            BotLogger.getInstance().logError("AliasManager: Save failed: " + e.getMessage());
        }
    }

    public void addAlias(String name, String command) {
        aliases.put(name.toLowerCase(), command);
        save();
    }

    public boolean removeAlias(String name) {
        boolean removed = aliases.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public String resolveAlias(String command) {
        if (command == null || command.isBlank()) return null;
        String[] parts = command.split("\\s+", 2);
        String aliasName = parts[0].toLowerCase();
        String mapped = aliases.get(aliasName);
        if (mapped == null) return null;
        return parts.length > 1 ? mapped + " " + parts[1] : mapped;
    }

    public Map<String, String> getAliases() {
        return Map.copyOf(aliases);
    }

    public Set<String> getAliasNames() {
        return Set.copyOf(aliases.keySet());
    }
}

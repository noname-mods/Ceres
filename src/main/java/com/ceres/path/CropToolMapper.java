package com.ceres.path;

import com.playerapi.PlayerInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps held tool display names to profile names.
 * Used on bot start to auto-load the right profile without the player needing to do it manually.
 *
 * <p>Matching is substring-based (case-insensitive) so reforged / tiered tools like
 * "Blessed Euclid's Wheat Hoe Mk. II" still match — the key part is always present
 * somewhere in the display name regardless of prefix or suffix.
 *
 * <p>Eclipse Hoe is a special case: it is used for both Sunflower and
 * Moonflower (same plant, different time of day).  Resolution priority:
 * <ol>
 *   <li>If both "Sunflower" and "Moonflower" profiles exist → use "Sunflower"</li>
 *   <li>If only one exists → use that one</li>
 *   <li>If neither exists → return null (no auto-load)</li>
 * </ol>
 */
public final class CropToolMapper {

    /**
     * All profile names this mapper can resolve to, in alphabetical order.
     * Used by BotConfig to build per-crop toggle storage and by CeresConfigScreen to build the UI.
     */
    public static final List<String> ALL_CROPS = List.of(
        "Cactus", "Carrot", "Cocoa Beans", "Melon", "Moonflower",
        "Mushroom", "Nether Wart", "Potato", "Pumpkin",
        "Sugarcane", "Sunflower", "Wheat", "Wild Rose"
    );

    /** Tool name substring (lowercase) → profile name. */
    private static final Map<String, String> TOOL_TO_PROFILE = new LinkedHashMap<>();

    static {
        TOOL_TO_PROFILE.put("cactus knife",    "Cactus");
        TOOL_TO_PROFILE.put("carrot hoe",      "Carrot");
        TOOL_TO_PROFILE.put("cocoa chopper",   "Cocoa Beans");
        TOOL_TO_PROFILE.put("fungi cutter",    "Mushroom");
        TOOL_TO_PROFILE.put("melon dicer",     "Melon");
        TOOL_TO_PROFILE.put("nether wart hoe", "Nether Wart");
        TOOL_TO_PROFILE.put("potato hoe",      "Potato");
        TOOL_TO_PROFILE.put("pumpkin dicer",   "Pumpkin");
        TOOL_TO_PROFILE.put("sugar cane hoe",  "Sugarcane");
        TOOL_TO_PROFILE.put("wheat hoe",       "Wheat");
        TOOL_TO_PROFILE.put("wild rose hoe",   "Wild Rose");
        // Eclipse Hoe (Sunflower/Moonflower) handled separately in resolveEclipseHoe()
    }

    private CropToolMapper() {}

    /**
     * Returns the profile name that should be auto-loaded for the player's currently held tool,
     * or {@code null} if the tool is not recognised or no matching profile exists on disk.
     */
    public static String resolveProfile() {
        String held = PlayerInfo.getHeldItem().displayName();
        if (held == null || held.isBlank()) return null;

        String lower = held.toLowerCase();

        if (lower.contains("eclipse hoe")) {
            return resolveEclipseHoe();
        }

        for (Map.Entry<String, String> entry : TOOL_TO_PROFILE.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static String resolveEclipseHoe() {
        List<String> profiles = ProfileManager.getInstance().listProfiles();
        boolean hasSunflower  = profiles.stream().anyMatch(p -> p.equalsIgnoreCase("Sunflower"));
        boolean hasMoonflower = profiles.stream().anyMatch(p -> p.equalsIgnoreCase("Moonflower"));

        if (hasSunflower)  return "Sunflower";
        if (hasMoonflower) return "Moonflower";
        return null;
    }
}

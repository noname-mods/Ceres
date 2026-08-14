package com.ceres.path;

import com.playerapi.PlayerInfo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identifies the player's held farm tool by its <b>SkyBlock internal id</b> (the item's NBT {@code id}
 * tag, e.g. {@code THEORETICAL_HOE_CARROT_3} or {@code CACTUS_KNIFE}) and maps it to a profile / crop.
 * This replaced display-name matching: Hypixel renames tool display names constantly, but the internal
 * id is stable and carries no reforge/tier styling.
 *
 * <h2>Id scheme</h2>
 * <ul>
 *   <li><b>{@code THEORETICAL_HOE_<CROP>_<tier>}</b> (tier 1/2/3 always numbered): Wheat, Carrot, Potato,
 *       plus special crop tokens {@code CANE} (sugar cane), {@code WARTS} (nether wart),
 *       {@code SUNFLOWER} (Eclipse — sun/moon), {@code WILD_ROSE}.</li>
 *   <li><b>Other tools</b> (tier 1 has no number, then {@code _2}/{@code _3}): {@code MELON_DICER},
 *       {@code PUMPKIN_DICER}, {@code FUNGI_CUTTER} (mushroom), {@code COCO_CHOPPER} (cocoa),
 *       {@code CACTUS_KNIFE}.</li>
 * </ul>
 * We match on the id <em>prefix</em> (ignoring the tier suffix), so all tiers resolve the same.
 *
 * <p>Eclipse Sickle is used for both Sunflower and Moonflower; {@link #resolveProfile()} picks the
 * Sunflower profile if present, else Moonflower, else null.</p>
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

    /** SkyBlock id prefix → crop/profile name. Matched with {@code startsWith} (tier suffix ignored). */
    private static final Map<String, String> ID_PREFIX_TO_CROP = new LinkedHashMap<>();
    /** Crop/profile name → vanilla block id path(s) it harvests (for the start-of-run crop check). */
    private static final Map<String, Set<String>> CROP_BLOCKS = new LinkedHashMap<>();
    /** Union of every known crop block path — used to tell "a different crop" from "not a crop". */
    private static final Set<String> ALL_CROP_BLOCKS = new HashSet<>();

    static {
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_WHEAT",     "Wheat");
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_CARROT",    "Carrot");
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_POTATO",    "Potato");
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_CANE",      "Sugarcane");
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_WARTS",     "Nether Wart");
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_SUNFLOWER", "Sunflower");   // Eclipse (sun/moon)
        ID_PREFIX_TO_CROP.put("THEORETICAL_HOE_WILD_ROSE", "Wild Rose");
        ID_PREFIX_TO_CROP.put("MELON_DICER",               "Melon");
        ID_PREFIX_TO_CROP.put("PUMPKIN_DICER",             "Pumpkin");
        ID_PREFIX_TO_CROP.put("FUNGI_CUTTER",              "Mushroom");
        ID_PREFIX_TO_CROP.put("COCO_CHOPPER",              "Cocoa Beans");
        ID_PREFIX_TO_CROP.put("CACTUS_KNIFE",              "Cactus");

        CROP_BLOCKS.put("Wheat",       Set.of("wheat"));
        CROP_BLOCKS.put("Carrot",      Set.of("carrots"));
        CROP_BLOCKS.put("Potato",      Set.of("potatoes"));
        CROP_BLOCKS.put("Sugarcane",   Set.of("sugar_cane"));
        CROP_BLOCKS.put("Nether Wart", Set.of("nether_wart"));
        CROP_BLOCKS.put("Sunflower",   Set.of("sunflower"));   // Eclipse (Moonflower is the same plant)
        CROP_BLOCKS.put("Melon",       Set.of("melon"));
        CROP_BLOCKS.put("Pumpkin",     Set.of("pumpkin"));
        CROP_BLOCKS.put("Cocoa Beans", Set.of("cocoa"));
        CROP_BLOCKS.put("Cactus",      Set.of("cactus"));
        CROP_BLOCKS.put("Mushroom",    Set.of(
                "red_mushroom", "brown_mushroom", "red_mushroom_block", "brown_mushroom_block", "mushroom_stem"));
        // "Wild Rose" crop block not reliably known → omitted (crop check skips it).

        for (Set<String> s : CROP_BLOCKS.values()) ALL_CROP_BLOCKS.addAll(s);
    }

    /** True if the block path is any known farmable crop (not farmland/dirt/air/path). */
    public static boolean isKnownCropBlock(String blockPath) {
        return ALL_CROP_BLOCKS.contains(blockPath);
    }

    private CropToolMapper() {}

    /** The canonical crop name for the held tool's SkyBlock id, or null if unrecognised. */
    private static String cropForHeldTool() {
        String id = PlayerInfo.getHeldItem().skyblockId();
        if (id == null || id.isBlank()) return null;
        for (Map.Entry<String, String> e : ID_PREFIX_TO_CROP.entrySet()) {
            if (id.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * Returns the profile name to auto-load for the player's currently held tool, or {@code null} if the
     * tool is not recognised. Eclipse Sickle resolves to Sunflower/Moonflower based on which profile exists.
     */
    public static String resolveProfile() {
        String id = PlayerInfo.getHeldItem().skyblockId();
        if (id == null || id.isBlank()) return null;
        if (id.startsWith("THEORETICAL_HOE_SUNFLOWER")) return resolveEclipseHoe();
        return cropForHeldTool();
    }

    /**
     * The vanilla block id path(s) the currently held farm tool should be breaking — for the
     * start-of-run crop check. Empty when the tool/crop isn't recognised (check is then skipped).
     */
    public static Set<String> expectedCropBlocks() {
        String crop = cropForHeldTool();
        if (crop == null) return Set.of();
        return CROP_BLOCKS.getOrDefault(crop, Set.of());
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

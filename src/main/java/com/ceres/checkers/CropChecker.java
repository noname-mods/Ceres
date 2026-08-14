package com.ceres.checkers;

import com.ceres.core.BotConfig;
import com.ceres.core.BotLogger;
import com.ceres.path.CropToolMapper;
import com.playerapi.PlayerInfo;
import com.playerapi.Scheduler;
import com.playerapi.WorldInfo;
import com.playerapi.types.BlockSnapshot;

import java.util.Optional;
import java.util.Set;

/**
 * One-shot crop/tool sanity check run for ~1 second after the bot starts: is the player actually looking
 * at the crop their held farm tool is meant to harvest? Samples the crosshair block each tick over the
 * window; if the majority of samples don't match the expected crop, plays an audio alert — it never stops
 * the bot (you won't be looking at a crop 100% of the time, so this is advisory only).
 *
 * <p>The expected crop comes from the held tool ({@link CropToolMapper#expectedCropBlocks()}); Eclipse
 * Sickle maps to sunflower (Moonflower is the same plant). If the tool/crop isn't recognised the check is
 * skipped entirely. Disable via {@link BotConfig#isCropCheckEnabled()}.</p>
 */
public final class CropChecker {

    private static final CropChecker INSTANCE = new CropChecker();
    public static CropChecker getInstance() { return INSTANCE; }
    private CropChecker() {}

    /** Length of the check window (~3s) — long enough to gather a representative set of crosshair samples
     *  as the bot walks the field, instead of a noisy 1s snapshot. */
    private static final int WINDOW_TICKS = 60;
    /** Wait this long after start before sampling, so /warp garden has finished repositioning us. */
    private static final int START_DELAY = 40;
    /** Need at least this many conclusive (crop) samples before we'll draw any conclusion — avoids acting
     *  on one or two stray hits. */
    private static final int MIN_SAMPLES = 4;

    private long startTick = -1;
    private long endTick = -1;
    private Set<String> expected = Set.of();
    /** Crosshair hit the expected crop. */
    private int matches = 0;
    /** Crosshair hit a *different* crop (the real "wrong tool for this field" signal). */
    private int wrongCrop = 0;

    /** Arm the check to run for ~1s once the post-start warp has settled. Call right after the bot starts. */
    public void begin() {
        startTick = -1;
        endTick = -1;
        if (!BotConfig.getInstance().isCropCheckEnabled()) return;
        expected = CropToolMapper.expectedCropBlocks();
        if (expected.isEmpty()) return; // unrecognised tool / unknown crop → skip

        matches = 0;
        wrongCrop = 0;
        startTick = Scheduler.getCurrentTick() + START_DELAY;
        endTick = startTick + WINDOW_TICKS;
    }

    /** Tick each RUNNING tick while armed. */
    public void tick(long currentTick) {
        if (endTick < 0) return;
        if (currentTick < startTick) return; // still waiting for the warp to settle

        Optional<BlockSnapshot> block = WorldInfo.getBlockAtCrosshair();
        if (block.isPresent()) {
            String id = stripNamespace(block.get().blockId());
            if (expected.contains(id)) {
                matches++;
            } else if (CropToolMapper.isKnownCropBlock(id)) {
                wrongCrop++; // a *different* crop — the only thing that signals the wrong tool
            }
            // Anything else (farmland / dirt / air / path) is inconclusive and ignored.
        }

        if (currentTick >= endTick) finish();
    }

    private void finish() {
        endTick = -1;
        int conclusive = matches + wrongCrop;
        // Never looked at a crop this window (all farmland/path/air) — inconclusive, no alert. We only
        // flag on a *different* crop; pure "no crop" is ignored so traversing bare farmland on the right
        // field never false-alerts.
        if (conclusive < MIN_SAMPLES) {
            BotLogger.getInstance().logDebug("CropChecker: too few crop samples (" + conclusive
                    + ") — inconclusive, no alert");
            return;
        }
        // Wrong crop is at least as common as the right one → you're almost certainly on the wrong field
        // for this tool (e.g. a potato hoe over carrots/wheat).
        if (wrongCrop >= matches) {
            BotLogger.getInstance().logWarn("CropChecker: held tool doesn't match the crop you're facing ("
                    + wrongCrop + " wrong vs " + matches + " right) — alerting");
            BotConfig.getInstance().getWarnAlertSound().play();
        } else {
            BotLogger.getInstance().logDebug("CropChecker: crop matches held tool ("
                    + matches + " right vs " + wrongCrop + " wrong)");
        }
    }

    private static String stripNamespace(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}

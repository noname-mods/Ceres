package com.ceres.checkers;

import com.ceres.core.BotLogger;
import com.playerapi.InventoryInfo;
import com.playerapi.types.ItemSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects a stalled inventory: harvested crops stop accumulating for too long (bot stuck / not breaking
 * crops).
 *
 * <p><b>Slots &amp; identity.</b> Harvested crops land in the storage rows, so we snapshot the
 * <b>full inventory</b> (all 36 slots) and compare a <b>stable per-slot signature</b> — vanilla item id +
 * SkyBlock id + stack count. That ignores a farm tool's live "cultivating" counter (display name / NBT),
 * which would otherwise look like a change every tick.</p>
 *
 * <p><b>Discrete 2s sampling (Personal Compactor edge case).</b> A Personal Compactor compacts every 160
 * of a crop into an enchanted item that goes to a <i>sack</i>, not the inventory — so if your crop total
 * passes a multiple of 160 the inventory can momentarily read "clean" even though you're actively farming.
 * To avoid false stalls we sample only every {@link #SAMPLE_INTERVAL} ticks (~2s) and require the same
 * signature <b>three samples in a row</b> ({@link #REQUIRED_MATCHES} consecutive matches after the
 * baseline) before flagging. That means a real stall takes ~4s of genuinely frozen inventory to trip,
 * and a single compactor blip between two farming samples can't line up across three.</p>
 *
 * <p><b>Startup exclusion.</b> The check is disabled for the first {@link #START_EXCLUDE_TICKS} ticks
 * (~10s) of every RUNNING session (a fresh start or a resume re-arms it), so warping in / getting going
 * never counts as a stall. This is separate from — and longer than — the shared 2s checker grace.</p>
 */
public class InventoryChecker {

    /** Ticks between inventory samples (~2s). */
    private static final int SAMPLE_INTERVAL = 40;
    /** Consecutive matching samples (after the baseline) required to flag — i.e. 3 identical in a row. */
    private static final int REQUIRED_MATCHES = 2;
    /** Inventory checks are suppressed for this long after a run starts/resumes (~10s). */
    private static final int START_EXCLUDE_TICKS = 200;

    /** The run (by start tick) we're currently armed for — a change means a new start/resume → re-arm. */
    private long runStartSeen = Long.MIN_VALUE;
    private long enableAfterTick = 0;
    private long nextSampleTick = 0;
    private List<String> baselineSig = null;
    private int consecutiveSame = 0;

    /**
     * @param currentTick   the current scheduler tick.
     * @param runStartTick  the tick the current RUNNING session began (from BotStateManager); a change
     *                      re-arms the startup exclusion and clears sampling state.
     * @return true if healthy, false if the inventory has been identical across three 2s samples.
     */
    public boolean check(long currentTick, long runStartTick) {
        // New run (start or resume) → re-arm the 10s exclusion, clear sampling state.
        if (runStartTick != runStartSeen) {
            runStartSeen = runStartTick;
            enableAfterTick = runStartTick + START_EXCLUDE_TICKS;
            nextSampleTick = enableAfterTick;   // first sample the moment checks go live
            baselineSig = null;
            consecutiveSame = 0;
            return true;
        }

        if (currentTick < enableAfterTick) return true;   // within the 10s startup exclusion
        if (currentTick < nextSampleTick) return true;    // not a sampling instant yet
        nextSampleTick = currentTick + SAMPLE_INTERVAL;

        List<String> current = signature();

        if (baselineSig == null) {
            baselineSig = current;
            consecutiveSame = 0;
            BotLogger.getInstance().logDebug("InventoryChecker: baseline sample taken");
            return true;
        }

        if (current.equals(baselineSig)) {
            consecutiveSame++;
            BotLogger.getInstance().logDebug("InventoryChecker: sample unchanged ("
                    + consecutiveSame + "/" + REQUIRED_MATCHES + ") — ~"
                    + (consecutiveSame * SAMPLE_INTERVAL / 20.0) + "s frozen");
            if (consecutiveSame >= REQUIRED_MATCHES) {
                BotLogger.getInstance().logDebug(
                        "InventoryChecker: STALL — inventory identical across 3 samples (~4s)");
                return false;
            }
        } else {
            BotLogger.getInstance().logDebug("InventoryChecker: sample changed ["
                    + firstDiff(baselineSig, current) + "] — reset");
            baselineSig = current;
            consecutiveSame = 0;
        }
        return true;
    }

    /**
     * Stable signature of the full inventory: {@code itemId|skyblockId|count} per slot. Deliberately omits
     * display name / damage / enchant glint so a tool's live counter can't masquerade as a real change.
     */
    private static List<String> signature() {
        List<ItemSnapshot> items = InventoryInfo.getMainInventory();
        List<String> sig = new ArrayList<>(items.size());
        for (ItemSnapshot it : items) {
            sig.add(it.itemId() + '|' + it.skyblockId() + '|' + it.count());
        }
        return sig;
    }

    /** First slot index whose signature differs, for debug — {@code slot N: old -> new}, or "same". */
    private static String firstDiff(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) {
                return "slot " + i + ": " + a.get(i) + " -> " + b.get(i);
            }
        }
        if (a.size() != b.size()) return "size " + a.size() + " -> " + b.size();
        return "same";
    }
}

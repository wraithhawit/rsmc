package com.wraithhawit.rsmbac.structure;

import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;

/**
 * What a structure costs to run, and how many patterns it can hold.
 *
 * <p>Both are functions of the block counts the shape code already returns, computed in one place
 * and charged once on the Controller. That is not a shortcut: the interior blocks have no block
 * entity, so there is no node to charge them on individually, and a per-node split would leave a
 * structure's CPUs and Pattern Storage running for free while its shell paid for everything.
 *
 * <p>Free of Minecraft types, like the rest of this package, so the numbers can be checked in a
 * plain JVM rather than by watching an energy bar.
 */
public final class StructurePower {
    /**
     * Patterns per Pattern Storage block.
     *
     * <p><strong>54, deliberately below Reborn Storage's 78.</strong> It is also the better number
     * for this screen: 54 is six full rows of nine, so one storage block's slots fill the grid
     * exactly, where 78 leaves a ragged part-row at every block boundary.
     *
     * <p>Still six times a stock Refined Storage autocrafter's nine.
     */
    public static final int PATTERNS_PER_STORAGE = 54;

    /** Energy per shell block. Cheap: they are structure, not machinery. */
    private static final long FRAME_COST = 1L;
    private static final long CASING_COST = 1L;

    /** The Controller itself, which is where the whole bill is charged. */
    private static final long CONTROLLER_COST = 8L;

    /** Energy per Pattern Storage block. Holding patterns is cheaper than executing them. */
    private static final long PATTERN_STORAGE_COST = 4L;

    private StructurePower() {
    }

    /**
     * Total energy draw, per tick.
     *
     * <p><strong>A CPU costs exactly what it produces</strong> -- its tier weight, the same number
     * it contributes to steps/tick. So energy tracks throughput rather than volume, and a big slow
     * structure is cheap while a small fast one is not. It also means the 1x/4x/16x/64x ladder is
     * energy-neutral: four 1x CPUs and one 4x cost the same and do the same, and the tier only buys
     * you space.
     *
     * <p>These are starting numbers, not a considered balance -- #5 is where that happens.
     */
    public static long energyUsage(final Result result) {
        if (!result.formed()) {
            return 0L;
        }
        final int shell = result.volume() - result.cpus() - result.patternStorages() - 1;
        // Frame and Casing cost the same, so the shell does not need splitting by kind. If they
        // ever differ, MultiblockShape has to start counting them separately -- it does not today,
        // deliberately, because nothing needed the distinction.
        final long shellCost = (long) shell * FRAME_COST;
        return CONTROLLER_COST
            + shellCost
            + (long) result.stepsPerTick() * 1L
            + (long) result.patternStorages() * PATTERN_STORAGE_COST;
    }

    /** How many patterns the structure can hold. */
    public static int patternCapacity(final Result result) {
        return result.formed() ? result.patternStorages() * PATTERNS_PER_STORAGE : 0;
    }

    /** Kept so the constant is reachable from a test without reflection. */
    static long casingCost() {
        return CASING_COST;
    }
}

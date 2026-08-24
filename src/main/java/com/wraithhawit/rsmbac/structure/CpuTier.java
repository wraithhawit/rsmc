package com.wraithhawit.rsmbac.structure;

/**
 * The four CPU tiers: 1x, 4x, 16x, 64x.
 *
 * <p><b>Renamed from 1K/4K/16K/64K in 0.1.11.</b> The old names borrowed Refined Storage's storage
 * ladder on the theory that a player already reads them as a four-step ×4 progression. They also
 * read as <em>capacity</em>, because that is what K means on every other block wearing it -- and
 * these do not store anything. A 64K CPU is not 64,000 of something; it is 64 steps per tick. The
 * x says the one true thing about it.
 *
 * <p>The rung ratio is still RS's, and the crafting recipes still ladder like RS's storage parts.
 * Only the label changed.
 *
 * <p><strong>The weight is steps per tick, directly.</strong> Not a multiplier on some base rate --
 * the number here is what one block of this tier contributes to the structure's crafting rate, and
 * a structure's rate is the sum over its CPU blocks. Keeping it a plain sum is what makes the
 * structure legible: a player can count blocks and know the answer, and adding a block never makes
 * anything slower.
 *
 * <h2>Where these numbers sit against a stock autocrafter</h2>
 *
 * <p>A Refined Storage autocrafter steps once every 10 ticks with no upgrades, and 5 times every 2
 * ticks with four speed upgrades -- so the stock range is <strong>0.1 to 2.5 steps/tick</strong>,
 * and 2.5 is the hard ceiling no amount of RS gear passes.
 *
 * <ul>
 *   <li>One 1x CPU is 1 step/tick: ten times a bare autocrafter, and still well under a maxed one.
 *       Deliberate -- the smallest structure should not obsolete the block it is competing with.
 *   <li>Three 1x CPUs pass a fully upgraded autocrafter.
 *   <li>A 4x4x4 half filled with 1x CPUs is 32 steps/tick, about 13 maxed autocrafters.
 * </ul>
 */
public enum CpuTier {
    ONE_X("1x", 1),
    FOUR_X("4x", 4),
    SIXTEEN_X("16x", 16),
    SIXTY_FOUR_X("64x", 64);

    private final String name;
    private final int stepsPerTick;

    CpuTier(final String name, final int stepsPerTick) {
        this.name = name;
        this.stepsPerTick = stepsPerTick;
    }

    /** The registry path segment, e.g. {@code cpu_1x}. */
    public String blockName() {
        return "cpu_" + this.name;
    }

    /** Steps per tick this one block contributes. */
    public int stepsPerTick() {
        return this.stepsPerTick;
    }
}

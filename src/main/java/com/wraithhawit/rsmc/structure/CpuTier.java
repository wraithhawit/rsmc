package com.wraithhawit.rsmc.structure;

/**
 * The four CPU tiers, named and weighted after Refined Storage's own storage blocks.
 *
 * <p>The naming is not decoration. The blocks are retextured RS storage blocks, so a player already
 * reads "1K, 4K, 16K, 64K" as RS's four-step ladder with a x4 rung; borrowing the same ladder for
 * speed means the tier they are holding tells them what it does without a tooltip.
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
 *   <li>One 1K CPU is 1 step/tick: ten times a bare autocrafter, and still well under a maxed one.
 *       Deliberate -- the smallest structure should not obsolete the block it is competing with.
 *   <li>Three 1K CPUs pass a fully upgraded autocrafter.
 *   <li>A 4x4x4 half filled with 1K CPUs is 32 steps/tick, about 13 maxed autocrafters.
 * </ul>
 */
public enum CpuTier {
    ONE_K("1k", 1),
    FOUR_K("4k", 4),
    SIXTEEN_K("16k", 16),
    SIXTY_FOUR_K("64k", 64);

    private final String name;
    private final int stepsPerTick;

    CpuTier(final String name, final int stepsPerTick) {
        this.name = name;
        this.stepsPerTick = stepsPerTick;
    }

    /** The registry path segment, e.g. {@code cpu_1k}. */
    public String blockName() {
        return "cpu_" + this.name;
    }

    /** Steps per tick this one block contributes. */
    public int stepsPerTick() {
        return this.stepsPerTick;
    }
}

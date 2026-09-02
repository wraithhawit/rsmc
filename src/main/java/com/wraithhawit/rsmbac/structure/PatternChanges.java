package com.wraithhawit.rsmbac.structure;

/**
 * A counter bumped whenever a pattern moves in or out of any Pattern Storage block, anywhere.
 *
 * <p>The sibling of {@link StructureChanges}, and separate from it for a reason that only became
 * visible once patterns could arrive from a pipe.
 *
 * <h2>What it fixes</h2>
 *
 * <p>The Controller hands changed patterns to the network node inside its structure refresh, and
 * since 0.1.9 that refresh is driven by {@link StructureChanges} -- which only counts blocks being
 * placed and removed. A pattern going into a storage block is not a block change, so it triggered
 * nothing, and the only thing that eventually noticed was the ten-second safety scan.
 *
 * <p>At eight patterns pushed per refresh that is <b>eight patterns every ten seconds</b>: filling
 * one 54-slot storage block took over a minute, during which the patterns are sitting in the world,
 * visible in the screen, and not craftable. By hand that reads as sluggish. Through a Pattern Port
 * feeding continuously it reads as broken.
 *
 * <h2>Why not simply bump {@link StructureChanges}</h2>
 *
 * <p>Because that counter is <em>debounced</em>: a change starts a quiet timer and every further
 * change restarts it, so that placing a 16x16x16 costs one scan instead of thousands. That is
 * exactly wrong for a pipe, which never stops changing things -- the window would restart forever
 * and the only scan that ever happened would be the ten-second safety one, which is the behaviour
 * being fixed.
 *
 * <p>So this is counted separately and {@link RefreshSchedule} treats it differently: rate-limited
 * rather than debounced. See {@link RefreshSchedule#PATTERN_TICKS}.
 *
 * <p>Like its sibling it remembers nothing and can only be wrong in one direction: a missed bump
 * costs a late push, never a wrong answer, because what gets pushed still comes from reading the
 * blocks.
 */
public final class PatternChanges {
    private static volatile long generation;

    private PatternChanges() {
    }

    /** Called from the Pattern Storage inventory listener, on every slot that changes. */
    public static void bump() {
        ++generation;
    }

    public static long generation() {
        return generation;
    }
}

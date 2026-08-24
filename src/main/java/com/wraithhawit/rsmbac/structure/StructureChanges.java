package com.wraithhawit.rsmbac.structure;

/**
 * A counter bumped whenever any rsmbac block is placed or removed, anywhere.
 *
 * <h2>Why this is not the state machine the design forbids</h2>
 *
 * <p>rsmbac deliberately keeps <b>no belief about the structure</b> -- see issue #3. Reborn
 * Storage's controller remembers an assembly state, and every chunk-load ordering, part attach
 * and controller merge is a chance for that belief to drift from the blocks actually in the
 * world. When the drifting object also holds your patterns, a desync is data loss.
 *
 * <p>This remembers nothing about any structure. It is a single number that says "something,
 * somewhere, changed" -- an invalidation hint, not a belief. Being wrong about it can only cost
 * a redundant scan or a slightly late one, never a wrong answer, because the answer still comes
 * from {@link MultiblockShape#find} reading the world.
 *
 * <h2>Why one global counter and not one per level or per region</h2>
 *
 * <p>A per-level or per-chunk map would invalidate more precisely, and buy nothing. The false
 * positive is "someone changed an rsmbac block in another dimension, so this controller rescans
 * once" -- which costs a bounded walk of at most 4096 positions, on an event that happens when a
 * player is building. Precision here would be machinery guarding against a cost that does not
 * matter.
 *
 * <p>Transient on purpose: it resets to zero on restart, and every controller's first tick after
 * load scans anyway because its safety interval has elapsed.
 */
public final class StructureChanges {
    private static volatile long generation;

    private StructureChanges() {
    }

    /** Called from every structure block's place and remove. */
    public static void bump() {
        ++generation;
    }

    public static long generation() {
        return generation;
    }
}

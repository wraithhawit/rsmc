package com.wraithhawit.rsmbac.structure;

/**
 * Decides when a Controller should re-derive its structure.
 *
 * <h2>What it replaces</h2>
 *
 * <p>Until 0.1.9 the Controller ran a full {@link MultiblockShape#find} -- a flood fill and a box
 * walk over up to 4096 positions -- <b>every 20 ticks, forever, whether or not anything had
 * changed</b>. Measured in a survival world at 0.19 ms/tick for a single block, which is the cost
 * of a machine that is doing nothing.
 *
 * <p>Two rules replace it, and together they are both cheaper and more responsive:
 *
 * <ul>
 *   <li><b>Change-driven.</b> Nothing is recomputed while {@link StructureChanges} says nothing
 *       has moved.</li>
 *   <li><b>Debounced.</b> A change starts a short quiet timer rather than scanning at once.
 *       Placing a 16³ by hand is thousands of block updates and a construction stick does it in
 *       one gesture; each of those should not walk the whole box. The window restarts on every
 *       further change, so a burst costs exactly one scan at the end of it.</li>
 * </ul>
 *
 * <h2>And a safety interval, because change-driven alone is not sound</h2>
 *
 * <p>The counter only sees <em>rsmbac</em> blocks. A structure can also be broken by something it
 * never hears about -- another mod placing a block inside the box, a fill command, anything
 * exotic. Those are rare, but "rare" is not "never", and a machine silently stuck in a wrong
 * state is a bug report nobody can reproduce.
 *
 * <p>So a scan also happens if none has for {@code safetyTicks}. At the default that is once
 * every ten seconds instead of once a second: an order of magnitude less idle work, while the
 * worst-case staleness for an exotic change goes from one second to ten. Ordinary changes are
 * noticed *faster* than before, in a quarter second rather than up to a full one.
 *
 * <p>Pure arithmetic over two longs, with time passed in, so it can be tested without a level.
 */
public final class RefreshSchedule {
    /** Quiet time after the last change before scanning. A quarter second. */
    public static final int DEBOUNCE_TICKS = 5;

    /** Longest a controller will go without scanning regardless. Ten seconds. */
    public static final int SAFETY_TICKS = 200;

    private final int debounceTicks;
    private final int safetyTicks;

    private long seenGeneration;
    private long pendingSince = -1L;
    private long lastScan;

    /**
     * Whether {@link #lastScan} means anything yet.
     *
     * <p>A flag rather than seeding {@code lastScan} with {@code Long.MIN_VALUE}: that version
     * looked obviously correct and was not, because {@code now - Long.MIN_VALUE} overflows to a
     * negative number, so "overdue" was never true and a freshly loaded Controller would have sat
     * there never deriving its structure. The self-tests caught it on their first run.
     */
    private boolean scanned;

    public RefreshSchedule() {
        this(DEBOUNCE_TICKS, SAFETY_TICKS);
    }

    public RefreshSchedule(final int debounceTicks, final int safetyTicks) {
        this.debounceTicks = Math.max(0, debounceTicks);
        this.safetyTicks = Math.max(1, safetyTicks);
    }

    /**
     * Whether to scan this tick, recording that a scan happened when the answer is yes.
     *
     * @param now        the current game time in ticks
     * @param generation the current {@link StructureChanges#generation()}
     */
    public boolean shouldScan(final long now, final long generation) {
        if (generation != this.seenGeneration) {
            this.seenGeneration = generation;
            // Restart the window rather than extending it: a burst of placements should cost one
            // scan after the burst, not one per change and not one halfway through.
            this.pendingSince = now;
        }
        final boolean settled =
            this.pendingSince >= 0 && now - this.pendingSince >= this.debounceTicks;
        // Checked even while a change is pending, so a player who never stops placing still gets
        // a periodically correct structure instead of one frozen until they pause.
        final boolean overdue = !this.scanned || now - this.lastScan >= this.safetyTicks;
        if (!settled && !overdue) {
            return false;
        }
        this.pendingSince = -1L;
        this.lastScan = now;
        this.scanned = true;
        return true;
    }

    /** Forces the next {@link #shouldScan} to say yes — used when the block entity loads. */
    public void invalidate() {
        this.scanned = false;
        this.pendingSince = -1L;
    }
}

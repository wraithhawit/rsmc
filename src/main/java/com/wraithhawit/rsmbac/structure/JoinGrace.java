package com.wraithhawit.rsmbac.structure;

/**
 * Decides whether a Controller's screen should be left alone because its node has not finished
 * joining a network yet.
 *
 * <h2>What it fixes</h2>
 *
 * <p>0.2.2's transition logging caught this on its first run in a real world:
 *
 * <pre>
 * active -&gt; inactive (tick 3069920, node active=false, energy       -1/176795 FE)
 * inactive -&gt; active (tick 3069926, node active=true,  energy MAX_VALUE/176795 FE)
 * </pre>
 *
 * <p>{@code -1} is "there is no network to ask". On world load the block entity ticks before
 * {@code joinNetworkIfNeeded} has a network to join, so the Controller reports no energy for a few
 * ticks and its screen is driven dark and straight back.
 *
 * <p>Six ticks on the server — and <b>permanent on the client</b>. A client whose chunk snapshot
 * lands inside that window is told INACTIVE; the server then returns to ACTIVE, sees no further
 * change, and has nothing left to broadcast. The two never reconcile. That is the "looks unpowered
 * but crafts normally" report, and why relogging cleared it. A convergence check that only fires on
 * change cannot repair a peer that missed the change.
 *
 * <h2>Bounded in both directions, deliberately</h2>
 *
 * <p><b>A screen that will not go dark is the worse bug</b>, so this suppresses as little as it
 * possibly can:
 *
 * <ul>
 *   <li>only the ACTIVE → INACTIVE direction — brightening and every UNFORMED transition go
 *       straight through;</li>
 *   <li>only while there is genuinely no network at all, not merely too little energy;</li>
 *   <li>only within {@link #GRACE_TICKS} of the first decision this instance is asked for.</li>
 * </ul>
 *
 * <p>So a structure whose cable was pulled while its chunk was unloaded reports honestly two
 * seconds after load rather than sitting lit forever.
 *
 * <p><b>The clock starts at the first call, not at zero.</b> The window is measured from the first
 * decision rather than against the level's game time, because a loaded world is at some arbitrary
 * tick — 3,069,920 in the log above. Comparing {@code now} against a constant would work in a test
 * that starts at tick 0 and never fire in a real save.
 *
 * <p>Pure arithmetic with time passed in, so it needs no level; see {@code HeadlessJoinGraceCheck}.
 */
public final class JoinGrace {
    /**
     * How long after loading a missing network reads as "not joined yet" rather than "unplugged".
     *
     * <p>Two seconds. The observed gap was six ticks, and joining does not take longer for a bigger
     * structure — it is one network rebuild — so this is generous by a factor of six rather than
     * tuned to the one measurement.
     */
    public static final long GRACE_TICKS = 40L;

    /** When this instance was first asked, or -1 before that. */
    private long firstDecision = -1L;

    /**
     * Whether to leave the screen showing ACTIVE for now.
     *
     * @param now         the current game time in ticks
     * @param darkening   whether the screen is about to go ACTIVE → INACTIVE
     * @param hasNetwork  whether the node has a network at all
     */
    public boolean suppressDarkening(final long now, final boolean darkening,
                                     final boolean hasNetwork) {
        if (this.firstDecision < 0L) {
            this.firstDecision = now;
        }
        return darkening && !hasNetwork && now - this.firstDecision < GRACE_TICKS;
    }
}

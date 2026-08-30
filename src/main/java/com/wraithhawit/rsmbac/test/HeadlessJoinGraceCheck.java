package com.wraithhawit.rsmbac.test;

import com.wraithhawit.rsmbac.structure.JoinGrace;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-tests for {@link JoinGrace}. Run with {@code ./gradlew joinGraceCheck}.
 *
 * <p>The grace period exists to stop a six-tick load-time flicker from stranding a client on a
 * dark screen forever. <b>The risk it introduces is the opposite bug</b> — a screen that will not
 * go dark when the structure really has been unplugged — so most of what is pinned here is the
 * suppression <em>ending</em>, not the suppression working.
 *
 * <p>Time is a parameter, so none of this needs a level or a clock.
 */
public final class HeadlessJoinGraceCheck {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    private HeadlessJoinGraceCheck() {
    }

    public static void main(final String[] args) {
        theObservedFlickerIsSuppressed();
        theGraceExpires();
        aStructureWithANetworkIsNeverSuppressed();
        brighteningIsNeverSuppressed();
        theClockStartsAtTheFirstCallNotAtZero();
        anUnplugLongAfterLoadGoesDarkImmediately();

        System.out.printf("scenarios: %d%n", checks);
        if (FAILURES.isEmpty()) {
            System.out.println("PASS");
            return;
        }
        FAILURES.forEach(f -> System.out.println("FAIL  " + f));
        System.out.printf("%d of %d scenarios failed%n", FAILURES.size(), checks);
        System.exit(1);
    }

    /** The exact gap from the log that found the bug: ticks 3069920 to 3069926, no network. */
    private static void theObservedFlickerIsSuppressed() {
        final JoinGrace grace = new JoinGrace();
        expect("the load tick itself is suppressed",
            grace.suppressDarkening(3069920L, true, false));
        expect("and so is the sixth tick, where the network actually arrived",
            grace.suppressDarkening(3069926L, true, false));
    }

    /**
     * The one that matters. If this ever fails, an unplugged structure sits lit forever and the
     * screen is lying in the direction that costs a player real time.
     */
    private static void theGraceExpires() {
        final JoinGrace grace = new JoinGrace();
        grace.suppressDarkening(1000L, true, false);
        expect("still suppressed one tick before the deadline",
            grace.suppressDarkening(1000L + JoinGrace.GRACE_TICKS - 1L, true, false));
        expect("NOT suppressed at the deadline",
            !grace.suppressDarkening(1000L + JoinGrace.GRACE_TICKS, true, false));
        expect("and never again after it",
            !grace.suppressDarkening(1000L + JoinGrace.GRACE_TICKS * 100L, true, false));
    }

    /**
     * Having a network but not enough energy is a real INACTIVE and must show. Only the total
     * absence of a network reads as "still joining".
     */
    private static void aStructureWithANetworkIsNeverSuppressed() {
        final JoinGrace grace = new JoinGrace();
        expect("a powered-down structure goes dark even on the load tick",
            !grace.suppressDarkening(500L, true, true));
    }

    /** INACTIVE to ACTIVE, and every UNFORMED transition, must go straight through. */
    private static void brighteningIsNeverSuppressed() {
        final JoinGrace grace = new JoinGrace();
        expect("lighting up is never held back, network or not",
            !grace.suppressDarkening(500L, false, false));
        expect("nor is it once a network exists",
            !grace.suppressDarkening(501L, false, true));
    }

    /**
     * A loaded world is at an arbitrary game time — 3,069,920 in the report that found this. If the
     * window were measured against the level clock rather than the first decision, it would pass at
     * tick 0 in a test and never fire in a real save.
     */
    private static void theClockStartsAtTheFirstCallNotAtZero() {
        final JoinGrace grace = new JoinGrace();
        final long loadedAt = 3_069_920L;
        expect("a world loaded three million ticks in still gets its grace",
            grace.suppressDarkening(loadedAt, true, false));
        expect("measured from the load, not from tick zero",
            grace.suppressDarkening(loadedAt + JoinGrace.GRACE_TICKS - 1L, true, false));
        expect("and it still expires",
            !grace.suppressDarkening(loadedAt + JoinGrace.GRACE_TICKS, true, false));
    }

    /** A cable pulled during play, long after load, must darken the screen on the spot. */
    private static void anUnplugLongAfterLoadGoesDarkImmediately() {
        final JoinGrace grace = new JoinGrace();
        grace.suppressDarkening(100L, false, true);
        expect("an unplug an hour into the session is not a join",
            !grace.suppressDarkening(100L + 72_000L, true, false));
    }

    private static void expect(final String what, final boolean ok) {
        checks++;
        if (!ok) {
            FAILURES.add(what);
        }
    }
}

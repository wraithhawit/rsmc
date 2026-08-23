package com.wraithhawit.rsmc.test;

import com.wraithhawit.rsmc.structure.RefreshSchedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-tests for {@link RefreshSchedule}. Run with {@code ./gradlew refreshCheck}.
 *
 * <p>The schedule is the whole of 0.1.9: a Controller used to walk up to 4096 positions once a
 * second forever, and now walks them when something changed. Getting that wrong is not a crash --
 * it is a machine that quietly stops noticing the world, or one that never stopped scanning and
 * saved nothing. Neither shows up as a failure anywhere, so it is pinned here.
 *
 * <p>Time and the change counter are both parameters, so none of this needs a level or a clock.
 */
public final class HeadlessRefreshCheck {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    private HeadlessRefreshCheck() {
    }

    public static void main(final String[] args) {
        scansOnceOnFirstTick();
        idleCostsNothingUntilTheSafetyInterval();
        aChangeScansAfterTheDebounce();
        aBurstCostsExactlyOneScan();
        theSafetyScanFiresDuringAnEndlessBurst();
        invalidateForcesAScan();
        theOldPollWouldHaveScanned10xMore();

        System.out.printf("scenarios: %d%n", checks);
        if (FAILURES.isEmpty()) {
            System.out.println("PASS");
            return;
        }
        FAILURES.forEach(f -> System.out.println("FAIL  " + f));
        System.out.printf("%d of %d scenarios failed%n", FAILURES.size(), checks);
        System.exit(1);
    }

    /** A freshly loaded chunk must derive its structure, not wait ten seconds to notice it. */
    private static void scansOnceOnFirstTick() {
        final RefreshSchedule schedule = new RefreshSchedule();
        expect("a new schedule scans immediately", schedule.shouldScan(0L, 0L));
        expect("and not again on the next tick", !schedule.shouldScan(1L, 0L));
    }

    private static void idleCostsNothingUntilTheSafetyInterval() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        int scans = 0;
        for (long t = 1; t <= RefreshSchedule.SAFETY_TICKS; t++) {
            if (schedule.shouldScan(t, 0L)) {
                ++scans;
            }
        }
        // Exactly one, at the safety interval. The old code would have done ten in this window.
        expect("an idle controller scans once per safety interval", scans == 1);
    }

    private static void aChangeScansAfterTheDebounce() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        expect("the change tick itself does not scan", !schedule.shouldScan(10L, 1L));
        for (long t = 11; t < 10 + RefreshSchedule.DEBOUNCE_TICKS; t++) {
            expect("still quiet at " + t, !schedule.shouldScan(t, 1L));
        }
        expect("scans once the debounce elapses",
            schedule.shouldScan(10L + RefreshSchedule.DEBOUNCE_TICKS, 1L));
    }

    /**
     * The case the debounce exists for: a construction stick placing a wall is one gesture and
     * hundreds of block updates.
     */
    private static void aBurstCostsExactlyOneScan() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        int scans = 0;
        long generation = 1;
        // 60 ticks of continuous placement, then quiet.
        for (long t = 1; t <= 60; t++) {
            if (schedule.shouldScan(t, generation++)) {
                ++scans;
            }
        }
        final int duringBurst = scans;
        for (long t = 61; t <= 61 + RefreshSchedule.DEBOUNCE_TICKS; t++) {
            if (schedule.shouldScan(t, generation)) {
                ++scans;
            }
        }
        expect("the burst settles into a scan", scans > duringBurst);
        // The safety interval can fire once inside a 60-tick burst; what must not happen is a
        // scan per change, which is what the naive "rescan on change" would do.
        expect("a 60-tick burst costs at most two scans, not sixty", scans <= 2);
    }

    /** A player who never stops placing must not freeze the structure's state indefinitely. */
    private static void theSafetyScanFiresDuringAnEndlessBurst() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        int scans = 0;
        long generation = 1;
        for (long t = 1; t <= RefreshSchedule.SAFETY_TICKS * 3; t++) {
            if (schedule.shouldScan(t, generation++)) {
                ++scans;
            }
        }
        expect("an endless burst still scans on the safety interval", scans >= 2);
    }

    private static void invalidateForcesAScan() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        expect("quiet after a scan", !schedule.shouldScan(1L, 0L));
        schedule.invalidate();
        expect("invalidate scans on the next tick", schedule.shouldScan(2L, 0L));
    }

    /** The measurement this whole change was for, stated as an assertion. */
    private static void theOldPollWouldHaveScanned10xMore() {
        final RefreshSchedule schedule = new RefreshSchedule();
        schedule.shouldScan(0L, 0L);
        int scans = 0;
        final long window = 20L * 60L;
        for (long t = 1; t <= window; t++) {
            if (schedule.shouldScan(t, 0L)) {
                ++scans;
            }
        }
        final long oldPollScans = window / 20L;
        expect("a minute idle: old poll 60 scans, new schedule 6",
            oldPollScans == 60 && scans == 6);
    }

    private static void expect(final String what, final boolean condition) {
        ++checks;
        if (!condition) {
            FAILURES.add(what);
        }
    }
}

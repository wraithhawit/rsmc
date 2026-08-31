package com.wraithhawit.rsmbac.test;

import com.wraithhawit.rsmbac.structure.CraftingBudget;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-tests for {@link CraftingBudget}. Run with {@code ./gradlew budgetCheck}.
 *
 * <p>The budget decides how much of a tick a structure may spend crafting, so its failure modes are
 * a world that still stutters (backs off too little) and a crafter that has stopped (backs off too
 * much and never recovers). Both are silent — neither throws, and neither shows up in any other
 * test — so they are pinned here.
 *
 * <p>Time and step counts are parameters, so none of this needs a level, a clock, or a structure.
 */
public final class HeadlessBudgetCheck {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    /** A 45ms budget in nanoseconds, which is the shipped default. */
    private static final long TARGET = 45L * 1_000_000L;

    /** A maxed 14x14x14 of 64x CPUs: (14^3 - 1) * 64. */
    private static final int MAXED = 175_552;

    private HeadlessBudgetCheck() {
    }

    public static void main(final String[] args) {
        anUnmeasuredStructureRunsAtFullRate();
        aBudgetOfZeroNeverThrottles();
        anOverrunBacksOffProportionallyAndAtOnce();
        headroomRecoversToTheFullRateAndStops();
        theAllowanceNeverRatchetsBelowOne();
        anIdleStructureDoesNotDriveTheAllowanceDown();
        aSteadyOverrunConvergesInsteadOfOscillating();
        switchingItOffForgetsTheOldAllowance();

        System.out.printf("budget checks: %d%n", checks);
        if (FAILURES.isEmpty()) {
            System.out.println("PASS");
            return;
        }
        FAILURES.forEach(f -> System.out.println("FAIL  " + f));
        System.out.printf("%d of %d checks failed%n", FAILURES.size(), checks);
        System.exit(1);
    }

    private static void anUnmeasuredStructureRunsAtFullRate() {
        final CraftingBudget budget = new CraftingBudget();
        expect("a structure that has never been timed runs at its full rate",
            budget.allowedSteps(MAXED, TARGET) == MAXED);
        expect("and does not report itself throttled", !budget.throttling(MAXED));
    }

    private static void aBudgetOfZeroNeverThrottles() {
        final CraftingBudget budget = new CraftingBudget();
        budget.record(TARGET * 10L, MAXED, MAXED, 0L);
        expect("a disabled budget leaves the full rate alone even after a huge overrun",
            budget.allowedSteps(MAXED, 0L) == MAXED);
    }

    /** The whole point: one long tick must be corrected on the next one, not gradually. */
    private static void anOverrunBacksOffProportionallyAndAtOnce() {
        final CraftingBudget budget = new CraftingBudget();
        // Four times the budget, so the allowance should land near a quarter.
        budget.record(TARGET * 4L, MAXED, MAXED, TARGET);
        final int allowed = budget.allowedSteps(MAXED, TARGET);
        expect("a 4x overrun cuts the allowance to about a quarter, immediately (got " + allowed
            + ")", allowed > MAXED / 5 && allowed < MAXED / 3);
        expect("and it reports itself throttled", budget.throttling(MAXED));
    }

    /**
     * The failure that would be silent: backing off and never coming back leaves a crafter that has
     * quietly stopped, on a server that looks healthy.
     */
    private static void headroomRecoversToTheFullRateAndStops() {
        final CraftingBudget budget = new CraftingBudget();
        budget.record(TARGET * 10L, MAXED, MAXED, TARGET);
        int allowed = budget.allowedSteps(MAXED, TARGET);
        expect("backed off first", allowed < MAXED);
        for (int tick = 0; tick < 500 && allowed < MAXED; tick++) {
            budget.record(TARGET / 2L, allowed, MAXED, TARGET);
            allowed = budget.allowedSteps(MAXED, TARGET);
        }
        expect("recovers all the way to the full rate when ticks fit", allowed == MAXED);
        expect("and stops there rather than overshooting",
            budget.allowedSteps(MAXED, TARGET) == MAXED);
        expect("and stops reporting itself throttled", !budget.throttling(MAXED));
    }

    private static void theAllowanceNeverRatchetsBelowOne() {
        final CraftingBudget budget = new CraftingBudget();
        for (int tick = 0; tick < 50; tick++) {
            budget.record(TARGET * 1000L, budget.allowedSteps(MAXED, TARGET), MAXED, TARGET);
        }
        expect("a structure that overruns catastrophically still gets one step, not zero",
            budget.allowedSteps(MAXED, TARGET) >= 1);
    }

    /**
     * An unformed or unpowered structure is given zero steps and therefore takes no time. Scaling on
     * that would read as "infinitely over budget" and drive the allowance to one, so that when the
     * structure came back it would crawl.
     */
    private static void anIdleStructureDoesNotDriveTheAllowanceDown() {
        final CraftingBudget budget = new CraftingBudget();
        for (int tick = 0; tick < 100; tick++) {
            budget.record(0L, 0, MAXED, TARGET);
        }
        expect("an idle structure is still allowed its full rate when it comes back",
            budget.allowedSteps(MAXED, TARGET) == MAXED);
    }

    /**
     * A structure whose work genuinely costs more than the budget should settle, not swing between
     * far too much and far too little -- a player would feel that as stutter.
     */
    private static void aSteadyOverrunConvergesInsteadOfOscillating() {
        final CraftingBudget budget = new CraftingBudget();
        // A cost model: every step costs the same, and the full rate would take four budgets.
        final double nanosPerStep = TARGET * 4.0 / MAXED;
        int allowed = budget.allowedSteps(MAXED, TARGET);
        int lowest = Integer.MAX_VALUE;
        int highest = 0;
        for (int tick = 0; tick < 200; tick++) {
            budget.record((long) (allowed * nanosPerStep), allowed, MAXED, TARGET);
            allowed = budget.allowedSteps(MAXED, TARGET);
            if (tick > 50) {
                lowest = Math.min(lowest, allowed);
                highest = Math.max(highest, allowed);
            }
        }
        final int ideal = (int) (TARGET / nanosPerStep);
        expect("settles near the rate the budget actually affords (got " + lowest + ".." + highest
                + ", ideal " + ideal + ")",
            lowest > ideal * 3 / 4 && highest < ideal * 3 / 2);
    }

    private static void switchingItOffForgetsTheOldAllowance() {
        final CraftingBudget budget = new CraftingBudget();
        budget.record(TARGET * 10L, MAXED, MAXED, TARGET);
        expect("throttled while on", budget.throttling(MAXED));
        budget.record(TARGET * 10L, MAXED, MAXED, 0L);
        expect("switching the budget off forgets the allowance rather than resuming from it",
            !budget.throttling(MAXED));
    }

    private static void expect(final String what, final boolean ok) {
        checks++;
        if (!ok) {
            FAILURES.add(what);
        }
    }
}

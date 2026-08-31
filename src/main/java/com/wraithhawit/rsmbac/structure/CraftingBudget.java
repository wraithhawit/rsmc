package com.wraithhawit.rsmbac.structure;

/**
 * Spreads a structure's crafting across ticks instead of cramming it into one.
 *
 * <h2>The problem it solves</h2>
 *
 * <p>A maxed structure asks Refined Storage for <b>175,552 steps per tick</b>, and RS performs them
 * synchronously before the tick can end. If that work takes 150ms the tick takes 150ms, the server
 * runs at about 6 TPS, and every mob, machine and chunk in the world gets those same 6 ticks. The
 * crafting is not wasted — it is real work the player asked for — but it is all taken at once.
 *
 * <h2>Why spreading costs almost nothing</h2>
 *
 * <p>The crafter currently gets ~100% of wall-clock time, because ticks run back to back and it
 * fills them. Under a budget it gets {@code target / 50ms} of wall-clock instead. With a 45ms
 * target and a world needing ~5ms a tick, that is 90% — <b>roughly a tenth less crafting for four
 * times the tick rate</b>.
 *
 * <p>This is what AE2's addons do structurally rather than by policy. ExtendedAE's Assembler Matrix
 * gives each crafter block a fixed set of threads on a cooldown, so its per-tick cost is
 * proportional to the number of threads and never to the number of crafts completed — a big job
 * takes <em>more ticks</em>, never a longer one. Refined Storage's task engine is iterative
 * ({@code for (i = 0; i < steps; i++)}), so we cannot adopt that shape without replacing the engine.
 * A budget applies the same idea to the engine we have.
 *
 * <h2>The control law, and why it is asymmetric</h2>
 *
 * <p>Back off <b>immediately and proportionally</b>; recover <b>slowly</b>. A long tick is the thing
 * being prevented, so overshooting the target once should correct on the next tick rather than
 * decay towards a correct value while the world stutters. Recovery is a gentle ramp because
 * repeatedly guessing high and getting cut back is itself an oscillation the player would feel.
 *
 * <p>The relationship between steps and elapsed time is unknown and varies with the craft — a step
 * that finds nothing to do costs almost nothing, one that extracts and substitutes costs a great
 * deal. Proportional control does not need to know the constant, which is exactly why it is used
 * here instead of any attempt to model the cost of a step.
 *
 * <h2>Nothing happens when nothing is wrong</h2>
 *
 * <p>While a tick fits inside the target the allowance climbs back to the structure's full rate and
 * stays there, so a small structure, an idle one, or a big one on a server with headroom behaves
 * exactly as it did before this existed. Only a structure that is <em>currently</em> overrunning is
 * throttled, and only for as long as it does.
 *
 * <p>Pure arithmetic with time passed in, so it is testable without a game; see
 * {@code HeadlessBudgetCheck}.
 */
public final class CraftingBudget {
    /** Not yet measured: the structure runs at its full rate until a tick has been timed. */
    private static final int UNMEASURED = -1;

    /**
     * How much of the allowance is added back per tick that fits, as a divisor.
     *
     * <p>An eighth: eight good ticks to undo one back-off, which at 20 TPS is under half a second
     * to recover and slow enough that a structure hovering at the limit does not visibly pulse.
     */
    private static final int RECOVERY_DIVISOR = 8;

    private int allowed = UNMEASURED;

    /** How many steps the structure may ask for this tick. */
    public int allowedSteps(final int structureRate, final long targetNanos) {
        if (targetNanos <= 0L || this.allowed == UNMEASURED) {
            return structureRate;
        }
        return Math.min(structureRate, this.allowed);
    }

    /**
     * Records what the last tick's crafting actually cost.
     *
     * @param elapsedNanos  how long {@code doWork} took
     * @param stepsGiven    what the structure was allowed to ask for on that tick
     * @param structureRate the structure's full rate, which is the ceiling
     * @param targetNanos   the budget; zero or less disables throttling entirely
     */
    public void record(final long elapsedNanos, final int stepsGiven, final int structureRate,
                       final long targetNanos) {
        if (targetNanos <= 0L) {
            // Switched off: forget everything, so turning it back on does not resume from a stale
            // allowance measured under different conditions.
            this.allowed = UNMEASURED;
            return;
        }
        if (stepsGiven <= 0) {
            // An inactive or unformed structure did no crafting, so the tick says nothing about
            // what the allowance should be. Scaling on it would drive the allowance to one.
            return;
        }
        if (elapsedNanos > targetNanos) {
            // Proportional, and applied at once. Long division rather than double arithmetic
            // because stepsGiven * targetNanos overflows an int at these magnitudes.
            final long scaled = (long) stepsGiven * targetNanos / elapsedNanos;
            this.allowed = (int) Math.max(1L, Math.min(structureRate, scaled));
            return;
        }
        final long base = this.allowed == UNMEASURED ? stepsGiven : this.allowed;
        this.allowed = (int) Math.min(structureRate, base + Math.max(1L, base / RECOVERY_DIVISOR));
    }

    /** What the structure is currently allowed, or the rate itself when unthrottled. */
    public int currentAllowance(final int structureRate) {
        return this.allowed == UNMEASURED ? structureRate : Math.min(structureRate, this.allowed);
    }

    /** Whether the budget is actually holding this structure back right now. */
    public boolean throttling(final int structureRate) {
        return this.allowed != UNMEASURED && this.allowed < structureRate;
    }
}

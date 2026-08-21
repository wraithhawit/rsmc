package com.wraithhawit.rsmc.structure;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.StepBehavior;

/**
 * The structure's crafting rate, in the only terms Refined Storage understands.
 *
 * <p>This is the whole reason the mod needs no mixins. RS models throughput as two questions asked
 * of a pattern provider -- may I step, and how many steps -- and a stock autocrafter answers them
 * with numbers from its speed upgrades: 1 step every 10 ticks bare, 5 every 2 ticks at maximum,
 * so 0.1 to 2.5 steps/tick and nothing in RS goes past that. A structure answers with the sum of
 * its CPU tier weights instead.
 *
 * <p><strong>Every tick, N steps</strong>, rather than fewer steps more often. The two are
 * equivalent in throughput and the first is simpler: there is no phase, no counter, and no way for
 * the rate to drift from the number {@code /rsmc info} prints.
 *
 * <p>The rate is per pattern, as far as RS is concerned, but a structure's speed is a property of
 * the structure -- so every pattern gets the same answer. A CPU does not know what it is making.
 */
public record StructureStepBehavior(int stepsPerTick, boolean active) implements StepBehavior {
    /** A structure that is not formed or not powered does nothing, rather than doing it slowly. */
    public static final StructureStepBehavior IDLE = new StructureStepBehavior(0, false);

    @Override
    public boolean canStep(final Pattern pattern) {
        return this.active && this.stepsPerTick > 0;
    }

    @Override
    public int getSteps(final Pattern pattern) {
        return this.active ? this.stepsPerTick : 0;
    }
}

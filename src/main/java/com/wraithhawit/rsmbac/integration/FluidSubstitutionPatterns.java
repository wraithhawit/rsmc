package com.wraithhawit.rsmbac.integration;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;

import com.ultramega.refinedfluidsubstitution.common.fluidsubstitutionpattern.FluidSubstitutionPatternItem;
import com.ultramega.refinedfluidsubstitution.common.fluidsubstitutionpattern.FluidSubstitutionPatternResolver;
import com.ultramega.refinedfluidsubstitution.common.util.PatternProviderNetworkNodeExtension;

import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.menu.StructurePatterns;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Makes a Fluid Substitution Pattern work inside the multiblock, the same way it works inside a
 * stock Refined Storage Autocrafter.
 *
 * <h2>Why this is needed at all</h2>
 *
 * <p>RFS does its resolution in a mixin on Refined Storage's own {@code AutocrafterBlockEntity}:
 * {@code rfs$updateFluidSubstitutionPatterns} reads that block entity's pattern container, resolves
 * each fluid-substitution pattern, and pushes the results into its {@code PatternProviderNetworkNode}.
 * Our Controller is not an {@code AutocrafterBlockEntity} — it keeps its patterns in Pattern Storage
 * blocks and pushes them into its own node — so that mixin never runs for us and the pattern reaches
 * the node <em>unresolved</em>, still asking for the literal bucket.
 *
 * <p>Reported in game as "I can't craft 1000 cakes", with the planner saying exactly that:
 * {@code required with no pattern and none in storage: [minecraft:milk_bucket]}. The two hooks RFS
 * does put on shared classes only manage pattern capacity; neither resolves anything.
 *
 * <h2>Deliberately the same layout as upstream</h2>
 *
 * <p>The scheme below is read from the 2.0.0 bytecode of {@code AutocrafterBlockEntityMixin} rather
 * than invented: main pattern at its own container slot, helper patterns packed sequentially from
 * {@code containerSize} upward, the rest of the helper region nulled, and capacity grown to
 * {@code containerSize * 10} — {@code MAX_HELPERS_PER_PATTERN} is 9 there, so ten slots per pattern
 * all told. Matching it means a pattern behaves identically in the multiblock and in an Autocrafter,
 * and that a future RFS change is a diff against something rather than a redesign.
 *
 * <p>Every RFS type in the mod is named here and nowhere else, so {@link FluidSubstitution} can gate
 * the whole integration on one {@code isLoaded} check.
 */
final class FluidSubstitutionPatterns {
    /** Upstream's {@code MAX_HELPERS_PER_PATTERN} (9) plus the pattern itself. */
    private static final int SLOTS_PER_PATTERN = 10;

    private FluidSubstitutionPatterns() {
    }

    static void refresh(final Level level,
                        final PatternProviderNetworkNode node,
                        final StructurePatterns patterns) {
        if (!(node instanceof PatternProviderNetworkNodeExtension capacity)) {
            // RFS is installed but its node mixin did not apply, so there is no room to put helper
            // patterns in. Resolving the main pattern without them would ask for a fluid nothing
            // can supply, which is worse than not resolving it at all.
            return;
        }
        final int size = patterns.getContainerSize();
        // Keyed by the helper's own id, because upstream derives those deterministically
        // (createHelperPatternUUID) and two slots substituting the same fluid produce the same
        // helper. Registering it twice would be two patterns making one thing.
        final Map<Object, Pattern> helpers = new LinkedHashMap<>();
        boolean any = false;
        for (int slot = 0; slot < size; slot++) {
            final ItemStack stack = patterns.getItem(slot);
            if (!(stack.getItem() instanceof FluidSubstitutionPatternItem)) {
                continue;
            }
            final int at = slot;
            final boolean[] resolved = {false};
            FluidSubstitutionPatternResolver.resolve(stack, level).ifPresent(pattern -> {
                node.setPattern(at, pattern.pattern());
                pattern.helperPatterns().forEach(helper -> helpers.put(helper.id(), helper));
                resolved[0] = true;
            });
            any |= resolved[0];
        }
        if (!any) {
            return;
        }
        capacity.rfs$ensurePatternCapacity(size * SLOTS_PER_PATTERN);
        final int limit = capacity.rfs$getPatternCapacity();
        int next = size;
        for (final Pattern helper : helpers.values()) {
            if (next >= limit) {
                // Out of room rather than out of helpers. Saying so is worth a line: the symptom
                // otherwise is one recipe quietly not crafting in a structure where everything else
                // works, which is the hardest kind of report to act on.
                RSMBAC.LOGGER.warn("[rsmbac] no room for every fluid substitution helper pattern "
                    + "({} slots for {} helpers); some fluid substitutions will not craft",
                    limit - size, helpers.size());
                break;
            }
            node.setPattern(next++, helper);
        }
        // Clear the tail, so a helper from a pattern that has since been removed cannot go on
        // advertising a craft the structure can no longer do.
        while (next < limit) {
            node.setPattern(next++, null);
        }
    }
}

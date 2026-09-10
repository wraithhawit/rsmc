package com.wraithhawit.rsmbac;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.content.DataComponents;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Which patterns this structure will hold: the ones Refined Storage runs itself, and no others.
 *
 * <h2>Why a processing pattern is refused rather than merely unsupported</h2>
 *
 * <p>A processing pattern pushes its ingredients into a machine and waits for the result, so it
 * needs a {@code PatternProviderExternalPatternSink}. An autocrafter is one. <strong>The multiblock
 * is not</strong>, and never calls {@code setPattern}'s companion {@code setSink} -- the whole point
 * of the mod is that it accelerates the crafting RS does internally, and you cannot parallelise a
 * furnace by building a bigger cube.
 *
 * <p>Left to itself, RS does not treat that as an error. {@code PatternProviderNetworkNode.accept}
 * opens with {@code if (sink == null) return SKIPPED}, so a processing pattern sitting in a
 * structure is still advertised as craftable, still gets a task planned and dispatched, and then
 * <strong>stalls forever</strong>. The recipe worked before it was moved here and silently does not
 * after, and it reads as the multiblock being broken rather than the pattern being in the wrong
 * machine.
 *
 * <p>So the rule is enforced at every door rather than documented as a caveat. {@link PatternImport}
 * already refused to carry these in; this is that same rule, hoisted out so the manual doors --
 * the pattern screen, the Pattern Port, and the push into the network node -- cannot disagree with
 * it or with each other.
 *
 * <h2>The test is the layout type, not the item</h2>
 *
 * <p>{@code INTERNAL} is a recipe RS runs; {@code EXTERNAL} is one it hands to a sink. That is what
 * RS itself dispatches on, so it is what is asked here. It is deliberately <em>not</em> "is this a
 * crafting pattern": stonecutter and smithing table patterns are internal too, they run here
 * perfectly well today, and narrowing the rule to crafting alone would break them for no reason.
 */
public final class PatternPolicy {
    private PatternPolicy() {
    }

    /**
     * Whether a resolved pattern is one this structure can run.
     *
     * <p>For callers that already hold the {@link Pattern} -- the push into the network node
     * resolves one per dirty slot regardless, so asking here costs a field read rather than a parse.
     */
    public static boolean runsHere(@Nullable final Pattern pattern) {
        return pattern != null && pattern.layout().type() == PatternType.INTERNAL;
    }

    /**
     * The authoritative test, for the server.
     *
     * <p>Resolves the pattern, which is not free -- see {@code StructurePatterns.canPlaceItem} for
     * what that cost when it was asked once per slot instead of once per stack. Every caller here is
     * either memoised or already resolving.
     *
     * <p>A stack that will not resolve at all is refused, which matches what RS's own
     * {@code PatternInventory} does with an unencoded pattern: an empty pattern in a slot is not a
     * recipe, and there is nothing for the node to be told about.
     */
    public static boolean runsHere(final Level level, final ItemStack stack) {
        return runsHere(RefinedStorageApi.INSTANCE.getPattern(stack, level).orElse(null));
    }

    /**
     * The cheap half of the same question, for the client.
     *
     * <p>Reads the pattern's own data component instead of resolving it. That matters: the client
     * asks its container whether an item fits <em>every frame</em> while the screen is open, and
     * resolving there was once 82.7% of the render thread -- the reason
     * {@code ClientPatternContainer} exists at all. A component read is a map lookup.
     *
     * <p>Only {@code PROCESSING} is external among the four types RS encodes, so this is exactly
     * equivalent for RS's own patterns and merely incomplete for anyone else's -- a pattern item
     * from another mod carries no {@link PatternState} and is let through here for the server to
     * refuse. Predicting one placement wrongly costs a corrected prediction; the server remains the
     * authority, as it already was for unencoded patterns.
     */
    public static boolean isKnownExternal(final ItemStack stack) {
        final PatternState state = stack.get(DataComponents.INSTANCE.getPatternState());
        return state != null
            && state.type()
            == com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType.PROCESSING;
    }
}

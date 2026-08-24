package com.wraithhawit.rsmbac.menu;

import com.refinedmods.refinedstorage.common.api.autocrafting.PatternProviderItem;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The client's copy of the structure's pattern slots.
 *
 * <p>Holds whatever vanilla's slot syncing puts in it, and answers one question of its own: may this
 * item go in a pattern slot. It exists because the answer has to be cheap.
 *
 * <h2>Why not Refined Storage's own PatternInventory</h2>
 *
 * <p>It was, briefly. Using RS's container made the client refuse a non-pattern exactly as the
 * server does, instead of predicting a shift-clicked cobblestone into a slot and having it bounce
 * back. That was the right instinct and the wrong implementation: RS's filter is
 * {@link PatternProviderItem#isValid}, which resolves the pattern's <em>recipe</em> --
 * a full {@code RecipeManager.getRecipeFor} scan over every recipe in the pack.
 *
 * <p>Something asks a menu's container whether an item fits on every frame, so that scan ran
 * continuously while the screen was open: <strong>82.7% of the client's render thread</strong>, and
 * the hitch that had been chased through four server profiles before anyone thought to look at the
 * client.
 *
 * <p>So this asks the cheap half of the same question -- is it a pattern item at all -- which is an
 * {@code instanceof} rather than a recipe lookup, and rejects everything a player is realistically
 * going to try. <strong>The server is still the authority</strong> and still runs the full check, so
 * nothing invalid can actually get in.
 *
 * <p>The one thing this predicts wrongly is an <em>unencoded</em> pattern: the client lets it move
 * and the server puts it back. That is a rare case, it costs a corrected prediction rather than a
 * lost item, and it is a fair price for not scanning every recipe in the game twenty times a second.
 */
public class ClientPatternContainer extends SimpleContainer {
    public ClientPatternContainer(final int size) {
        super(size);
    }

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        return stack.getItem() instanceof PatternProviderItem;
    }
}

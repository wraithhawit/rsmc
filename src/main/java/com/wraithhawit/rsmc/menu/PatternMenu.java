package com.wraithhawit.rsmc.menu;

import java.util.ArrayList;
import java.util.List;

import com.refinedmods.refinedstorage.common.autocrafting.PatternInventory;
import com.refinedmods.refinedstorage.common.autocrafting.PatternSlot;
import com.refinedmods.refinedstorage.common.support.AbstractBaseContainerMenu;
import com.refinedmods.refinedstorage.common.support.stretching.ScreenSizeListener;

import com.wraithhawit.rsmc.content.RsmcMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The structure's pattern slots, plus the player's inventory.
 *
 * <p>Modelled on {@code AutocrafterManagerContainerMenu}, closely enough that the differences are
 * the interesting part: it has one group per autocrafter on the network, this has one flat list
 * from the Pattern Storage blocks inside one structure.
 *
 * <h2>Slots are built twice, and that is not a mistake</h2>
 *
 * <p>The constructor builds them, and {@link #resized} rebuilds them once the screen knows how tall
 * the window is. RS does exactly this, and the reason is easy to miss: <strong>{@code resized} only
 * ever runs on the client</strong>, because it is the screen that calls it. A menu that only adds
 * slots there has none at all on the server -- so every slot index a click sends refers to nothing,
 * and shift-clicking silently does nothing at all. That was a real bug here before this was copied
 * properly.
 *
 * <p>Slot <em>positions</em> remain a client concern -- a click is sent as an index, never a
 * coordinate -- which is what lets the screen move slots freely to scroll and filter.
 *
 * <h2>The two sides are backed by different things, on purpose</h2>
 *
 * <p>On the server the slots read straight through {@link StructurePatterns} into the Pattern
 * Storage block entities. On the client there is no structure to read, so they are backed by a
 * {@link PatternInventory} of the same size -- RS's own, so the client filters non-patterns exactly
 * as the server does rather than predicting them in and having them bounce back.
 */
public class PatternMenu extends AbstractBaseContainerMenu implements ScreenSizeListener {
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;

    /** Left edge of both grids, matching the Autocrafter Manager. */
    public static final int SLOTS_X = 8;

    private final Container patterns;
    private final Inventory playerInventory;
    private final Level level;
    private final List<Slot> patternSlots = new ArrayList<>();

    /** Server side: real patterns, in the real blocks. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final StructurePatterns patterns) {
        this(containerId, playerInventory, patterns, playerInventory.player.level());
    }

    /** Client side: RS's own pattern container, so it filters identically. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory,
            new PatternInventory(buf.readVarInt(), () -> playerInventory.player.level()),
            playerInventory.player.level());
    }

    private PatternMenu(final int containerId, final Inventory playerInventory,
                        final Container patterns, final Level level) {
        super(RsmcMenus.PATTERNS.get(), containerId);
        this.patterns = patterns;
        this.playerInventory = playerInventory;
        this.level = level;
        this.buildSlots(0);
        // Patterns move between the player and the structure, and nothing else moves either way.
        this.transferManager.addBiTransfer(playerInventory, patterns);
    }

    /**
     * (Re)creates every slot. Positions here are provisional on the client -- the screen moves the
     * pattern slots as it scrolls and filters -- but on the server they are never touched again, and
     * only their existence matters.
     */
    private void buildSlots(final int playerInventoryY) {
        this.resetSlots();
        this.patternSlots.clear();
        for (int i = 0; i < this.patterns.getContainerSize(); i++) {
            final Slot slot = new PatternSlot(this.patterns, i,
                SLOTS_X + i % COLUMNS * SLOT_SIZE,
                i / COLUMNS * SLOT_SIZE,
                this.level);
            this.patternSlots.add(this.addSlot(slot));
        }
        this.addPlayerInventory(this.playerInventory, SLOTS_X, playerInventoryY);
    }

    /** In menu order, which is {@link StructurePatterns}' order: by block position. */
    public List<Slot> patternSlots() {
        return this.patternSlots;
    }

    /**
     * Puts the player's inventory where the stretched window ends.
     *
     * <p>A stretching screen has no fixed height, so this is the only notification that the window
     * changed size. Rebuilding rather than nudging positions is RS's approach and is simpler: one
     * method decides where every slot goes.
     */
    @Override
    public void resized(final int playerInventoryY, final int topYStart, final int topYEnd) {
        this.buildSlots(playerInventoryY);
    }

    public int patternSlotCount() {
        return this.patterns.getContainerSize();
    }

    /**
     * Whether this exact stack is a pattern sitting in one of the structure's slots.
     *
     * <p>Lifted from {@code AutocrafterManagerContainerMenu.containsPattern}, <strong>reference
     * equality and all</strong>. That looks like a bug and is not: it identifies the one stack
     * instance being drawn, so a pattern in a structure slot renders as its output while the very
     * same pattern in the player's inventory below still renders as a pattern.
     */
    public boolean containsPattern(final ItemStack stack) {
        for (final Slot slot : this.patternSlots) {
            if (slot.getItem() == stack) {
                return true;
            }
        }
        return false;
    }

    // Shift-click is RS's TransferManager, declared in the constructor. The base class's
    // quickMoveStack already delegates to it, so there is nothing to override.

    @Override
    public boolean stillValid(final Player player) {
        // The structure is re-read whenever it matters rather than watched: if the player breaks the
        // box while the screen is open, the slots stop being backed by anything and the container
        // answers empty, which is the honest result. Closing the screen on a block change would also
        // fire when they place one.
        return true;
    }
}

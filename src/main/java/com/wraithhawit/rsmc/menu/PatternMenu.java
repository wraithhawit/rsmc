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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The structure's pattern slots, plus the player's inventory.
 *
 * <p>Every pattern slot the structure has exists in this menu from the moment it opens -- all of
 * them, however many Pattern Storage blocks are in the box. The screen decides which are on screen
 * by moving them; nothing here knows about scrolling or searching.
 *
 * <p>That split matters. Slot <em>positions</em> are a client concern: a click is sent as a slot
 * index, never as a coordinate, so the screen can move slots around freely and the server never has
 * to agree about layout. Filtering by search text is therefore free, and cannot desync.
 *
 * <h2>The two sides are backed by different things, on purpose</h2>
 *
 * <p>On the server the slots read straight through {@link StructurePatterns} into the Pattern
 * Storage block entities -- the real patterns, in the real blocks. On the client there is no
 * structure to read, so they are backed by a plain container of the same size that vanilla's slot
 * syncing fills in. The client is told only how many slots there are, because that is the only
 * thing it needs and the only thing it could not work out.
 */
public class PatternMenu extends AbstractBaseContainerMenu implements ScreenSizeListener {
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;

    /** Where the pattern grid starts, and where the screen puts slots back when it lays them out. */
    public static final int PATTERNS_X = 8;
    public static final int PATTERNS_Y = 32;

    /** Rows visible at once before scrolling. Matches the Autocrafter Manager. */
    public static final int VISIBLE_ROWS = 4;

    private final Container patterns;
    private final List<Slot> patternSlots = new ArrayList<>();
    private final Inventory playerInventory;

    /** Server side: real patterns, in the real blocks. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final StructurePatterns patterns) {
        this(containerId, playerInventory, patterns, playerInventory.player.level());
    }

    /** Client side: a container of the right size, which vanilla slot syncing fills. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final RegistryFriendlyByteBuf buf) {
        // A PatternInventory, not a plain container: it filters with RS's own
        // PatternProviderItem.isValid, so the client refuses a non-pattern exactly as the server
        // does. With a SimpleContainer here the client happily predicted a shift-clicked
        // cobblestone into a pattern slot and only the server's correction took it back out.
        this(containerId, playerInventory,
            new PatternInventory(buf.readVarInt(), () -> playerInventory.player.level()),
            playerInventory.player.level());
    }

    private PatternMenu(final int containerId, final Inventory playerInventory,
                        final Container patterns, final Level level) {
        super(RsmcMenus.PATTERNS.get(), containerId);
        this.patterns = patterns;
        for (int i = 0; i < patterns.getContainerSize(); i++) {
            // Laid out in a grid the screen immediately overrides. The positions still have to be
            // sane, because a slot that is never laid out would otherwise sit at 0,0 under the
            // title bar and be clickable.
            final int row = i / COLUMNS;
            final int column = i % COLUMNS;
            final Slot slot = new PatternSlot(patterns, i,
                PATTERNS_X + column * SLOT_SIZE,
                PATTERNS_Y + row * SLOT_SIZE,
                level);
            this.patternSlots.add(this.addSlot(slot));
        }
        this.playerInventory = playerInventory;
        // Patterns move between the player and the structure, and nothing else moves either way.
        this.transferManager.addBiTransfer(playerInventory, patterns);
    }

    /** In menu order, which is {@link StructurePatterns}' order: by block position. */
    public List<Slot> patternSlots() {
        return this.patternSlots;
    }

    /**
     * Puts the player's inventory where the stretched window ends.
     *
     * <p>Called by {@code AbstractStretchingScreen} once it knows how many rows fit, with the y the
     * player inventory should start at. <strong>It is not optional.</strong> A stretching screen has
     * no fixed height, so inventory slots given a position at construction end up wherever the
     * window happened to be that size -- which in practice was floating in the middle of the pattern
     * area while the drawn inventory at the bottom sat empty.
     *
     * <p>An earlier version left this empty on the reasoning that the screen owns layout. That is
     * true of the <em>pattern</em> slots, which the screen moves for scrolling and filtering. The
     * player's inventory is the opposite case: it never moves except when the window resizes, and
     * this is the only notification that it did.
     */
    @Override
    public void resized(final int playerInventoryY, final int topYStart, final int topYEnd) {
        // RS's own helper, the same call AutocrafterManagerContainerMenu.initializeGroups makes.
        // An earlier version positioned the four rows by hand and got the hotbar gap from a
        // constant; this is one line and cannot disagree with how every other RS screen looks.
        this.addPlayerInventory(this.playerInventory, PATTERNS_X, playerInventoryY);
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
     * same pattern in the player's inventory below still renders as a pattern. Comparing by value
     * would turn the inventory into outputs too.
     */
    public boolean containsPattern(final ItemStack stack) {
        for (final Slot slot : this.patternSlots) {
            if (slot.getItem() == stack) {
                return true;
            }
        }
        return false;
    }

    // Shift-click is RS's TransferManager, configured in the constructor: a pattern goes from the
    // player into the structure and back, and nothing else moves either way. The base class's
    // quickMoveStack already delegates to it, so there is nothing to override -- an earlier version
    // hand-rolled the whole thing with moveItemStackTo.

    @Override
    public boolean stillValid(final Player player) {
        // The structure is re-read whenever it matters rather than watched: if the player breaks
        // the box while the screen is open, the slots they can see stop being backed by anything
        // and the container answers empty, which is the honest result. Closing the screen out from
        // under them on a block change would be worse -- it would also fire when they place a
        // block.
        return true;
    }
}

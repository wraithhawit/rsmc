package com.wraithhawit.rsmc.menu;

import java.util.ArrayList;
import java.util.List;

import com.refinedmods.refinedstorage.common.autocrafting.PatternSlot;

import com.wraithhawit.rsmc.content.RsmcMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
public class PatternMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;

    /** Where the pattern grid starts, and where the screen puts slots back when it lays them out. */
    public static final int PATTERNS_X = 8;
    public static final int PATTERNS_Y = 32;

    /** Rows visible at once before scrolling. Matches the Autocrafter Manager. */
    public static final int VISIBLE_ROWS = 4;

    private final Container patterns;
    private final List<Slot> patternSlots = new ArrayList<>();

    /** Server side: real patterns, in the real blocks. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final StructurePatterns patterns) {
        this(containerId, playerInventory, patterns, playerInventory.player.level());
    }

    /** Client side: a container of the right size, which vanilla slot syncing fills. */
    public PatternMenu(final int containerId, final Inventory playerInventory,
                       final RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(buf.readVarInt()),
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
        this.addPlayerInventory(playerInventory);
    }

    /** In menu order, which is {@link StructurePatterns}' order: by block position. */
    public List<Slot> patternSlots() {
        return this.patternSlots;
    }

    public int patternSlotCount() {
        return this.patterns.getContainerSize();
    }

    private void addPlayerInventory(final Inventory playerInventory) {
        final int inventoryY = PATTERNS_Y + VISIBLE_ROWS * SLOT_SIZE + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(playerInventory, column + row * COLUMNS + 9,
                    PATTERNS_X + column * SLOT_SIZE, inventoryY + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            this.addSlot(new Slot(playerInventory, column,
                PATTERNS_X + column * SLOT_SIZE, inventoryY + 58));
        }
    }

    /**
     * Shift-clicking moves patterns between the player and the structure.
     *
     * <p>Deliberately does not fall back to "try every slot": a pattern shift-clicked from the
     * player goes into the structure and nowhere else, and one shift-clicked out of the structure
     * goes to the player. Anything that is not a pattern cannot enter the structure at all, because
     * {@link PatternSlot} refuses it.
     */
    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int patternCount = this.patternSlots.size();
        final boolean fromStructure = index < patternCount;
        final boolean moved = fromStructure
            ? this.moveItemStackTo(stack, patternCount, this.slots.size(), true)
            : this.moveItemStackTo(stack, 0, patternCount, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

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

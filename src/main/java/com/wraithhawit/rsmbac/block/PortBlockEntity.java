package com.wraithhawit.rsmbac.block;

import javax.annotation.Nullable;

import com.wraithhawit.rsmbac.content.RsmcBlockEntities;
import com.wraithhawit.rsmbac.menu.StructurePatterns;
import com.wraithhawit.rsmbac.structure.StructureChanges;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Pattern Port's item handler: two virtual slots over the whole structure's patterns.
 *
 * <h2>Two slots, and neither of them is real</h2>
 *
 * <ul>
 *   <li><b>Slot 0 is insert-only.</b> It always reads as empty, so a pipe always sees room, and an
 *       insert goes to the structure's first free pattern slot wherever that is.
 *   <li><b>Slot 1 is extract-only.</b> It reads as the single most recently filled pattern, and
 *       extracting takes that one.
 * </ul>
 *
 * <p>The shape is taken from Reborn Storage's {@code TileIoPort}, which does the same thing with
 * the same two slots, and it is better than the obvious alternative in two separate ways.
 *
 * <p><strong>Nothing can drain the structure.</strong> The obvious design is to expose the pattern
 * inventory as an item handler with one slot per pattern. Do that and a Refined Storage External
 * Storage pointed at the Port lists every pattern in the crafter as a network item -- and either
 * hands them out, or, if extraction is blocked, lists thousands of items it will not give you. A
 * one-stack window has neither problem: automation can take patterns back out, one at a time, and
 * can never see more than one.
 *
 * <p><strong>Nothing walks the slots.</strong> {@code ItemHandlerHelper.insertItem} tries every
 * slot of a handler in turn until one accepts. A maxed structure holds around 148,000 pattern
 * slots, so a real handler would have every hopper walking all of them on every failed push. Two
 * slots cannot be walked, and finding the free one is a question the storage blocks answer from a
 * bound they already keep -- see {@link PatternStorageBlockEntity#firstFreeSlot()}.
 *
 * <h2>An unformed structure refuses, it does not swallow</h2>
 *
 * <p>{@link StructurePatterns#of} returns an empty view when the box is not a legal structure, and
 * an empty view has no free slot, so the insert is declined and the stack stays in the pipe. That
 * is the important direction to fail in: a Port that accepted patterns into a broken structure
 * would be destroying them.
 */
public class PortBlockEntity extends ShellBlockEntity implements IItemHandler {
    /** Patterns go in here. Always reads empty. */
    private static final int SLOT_IN = 0;

    /** Patterns come out here. Reads as the last one that went in. */
    private static final int SLOT_OUT = 1;

    /**
     * The structure's patterns, kept between calls.
     *
     * <p>Building this walks the interior -- up to 2,744 positions, each a chunk lookup -- and a
     * hopper pushes at every opportunity, so rebuilding it per insert is not affordable.
     *
     * <p><b>This is a cache, in a mod that deliberately keeps no belief about the structure.</b> It
     * is allowed for the same reason the Controller's {@code builtCapacity} is: it decides nothing,
     * it only avoids recomputing something, and it is thrown away the moment it might be stale. Two
     * things can make it stale and both are checked before every use -- any rsmbac block moving,
     * which {@link StructureChanges} counts, and a chunk reload replacing the block entities, which
     * {@link StructurePatterns#storagesStillLive()} catches. Being wrong here would mean writing
     * patterns into discarded objects, which is item loss, so it is checked rather than reasoned
     * about.
     */
    @Nullable
    private StructurePatterns patterns;

    /** The {@link StructureChanges#generation()} that {@link #patterns} was built at. */
    private long patternsGeneration;

    public PortBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.PORT.get(), pos, state);
    }

    @Override
    protected String containerName() {
        return "port";
    }

    /**
     * The structure's patterns, rebuilt only when they cannot be trusted.
     *
     * @return null on the client, or with no level -- there is no structure to read either way
     */
    @Nullable
    private StructurePatterns patterns() {
        final Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return null;
        }
        final long generation = StructureChanges.generation();
        if (this.patterns == null
            || this.patternsGeneration != generation
            || !this.patterns.storagesStillLive()) {
            this.patterns = StructurePatterns.of(currentLevel, this.worldPosition);
            this.patternsGeneration = generation;
        }
        return this.patterns;
    }

    @Override
    public int getSlots() {
        return 2;
    }

    /**
     * Patterns do not stack -- {@code PatternInventory.getMaxStackSize()} is 1 -- so nor does this.
     */
    @Override
    public int getSlotLimit(final int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        if (slot != SLOT_IN) {
            return false;
        }
        final StructurePatterns view = this.patterns();
        return view != null && view.accepts(stack);
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        if (slot != SLOT_OUT) {
            return ItemStack.EMPTY;
        }
        final StructurePatterns view = this.patterns();
        if (view == null) {
            return ItemStack.EMPTY;
        }
        final int last = view.lastOccupiedSlot();
        return last < 0 ? ItemStack.EMPTY : view.getItem(last);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (slot != SLOT_IN || stack.isEmpty()) {
            return stack;
        }
        final StructurePatterns view = this.patterns();
        if (view == null || !view.accepts(stack)) {
            return stack;
        }
        final int free = view.firstFreeSlot();
        if (free < 0) {
            return stack;
        }
        if (!simulate) {
            // One, never the whole stack. A pattern slot holds a single item, and writing a bigger
            // count into it puts a stack the inventory cannot represent where RS will read it back.
            view.setItem(free, stack.copyWithCount(1));
        }
        return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        if (slot != SLOT_OUT || amount <= 0) {
            return ItemStack.EMPTY;
        }
        final StructurePatterns view = this.patterns();
        if (view == null) {
            return ItemStack.EMPTY;
        }
        final int last = view.lastOccupiedSlot();
        if (last < 0) {
            return ItemStack.EMPTY;
        }
        if (simulate) {
            return view.getItem(last).copy();
        }
        // removeItem, not removeItemNoUpdate: only the former tells PatternInventory's listener,
        // and that listener is the entire path by which the Controller learns the slot is empty and
        // clears the pattern out of the network node. Without it the crafter would go on
        // advertising a recipe whose pattern has been pulled out and piped somewhere else.
        return view.removeItem(last, 1);
    }
}

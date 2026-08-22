package com.wraithhawit.rsmc.menu;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Every pattern slot in one structure, as a single container.
 *
 * <p>The pattern screen is a <em>view</em> over the structure's Pattern Storage blocks, not a
 * container that owns anything -- the patterns stay in the blocks the whole time. This is what
 * makes that view addressable: slot 0 is the first slot of the first storage block, and the
 * arithmetic to find which block a slot belongs to lives here and nowhere else.
 *
 * <h2>Ordered by position, deliberately</h2>
 *
 * <p>Storage blocks are sorted by their coordinates rather than by the order the scan happened to
 * find them. A player's patterns must not move around in the screen because a chunk reloaded, and
 * "which slot is slot 40" has to mean the same thing on the server and on the client, which agree
 * on nothing except the world.
 */
public final class StructurePatterns implements Container {
    private final List<PatternStorageBlockEntity> storages;
    private final int size;
    private final PatternStorageBlockEntity[] slotToStorage;
    private final int[] slotToLocalIndex;

    /**
     * One-entry memo for {@link #canPlaceItem}. Not persisted, not shared, and thrown away with the
     * view -- a view lives for one menu or one refresh.
     */
    @Nullable
    private ItemStack lastCheckedStack;
    private boolean lastCheckedResult;

    private StructurePatterns(final List<PatternStorageBlockEntity> storages) {
        this.storages = storages;
        int total = 0;
        for (final PatternStorageBlockEntity storage : storages) {
            total += storage.patterns().getContainerSize();
        }
        this.size = total;
        // Slot -> (block, index within it), resolved once.
        //
        // These used to be two linear walks over the storage blocks on every access, and
        // getItem is called for every slot every tick while the screen is open --
        // 54 slots per storage, twenty times a second. It showed up at 2.9% of the server thread
        // in a profile with the screen open. Building the map costs one pass and makes every later
        // lookup an array read.
        this.slotToStorage = new PatternStorageBlockEntity[total];
        this.slotToLocalIndex = new int[total];
        int slot = 0;
        for (final PatternStorageBlockEntity storage : storages) {
            final int capacity = storage.patterns().getContainerSize();
            for (int local = 0; local < capacity; local++) {
                this.slotToStorage[slot] = storage;
                this.slotToLocalIndex[slot] = local;
                slot++;
            }
        }
    }

    /**
     * Gathers the Pattern Storage blocks of the structure containing {@code seed}.
     *
     * <p>Empty when the structure is not formed -- a half-built box has no pattern screen, and the
     * player is told why by {@code /rsmc info} rather than by an empty window.
     */
    public static StructurePatterns of(final Level level, final BlockPos seed) {
        return of(level, MultiblockShape.find(
            new LevelBlockSource(level), seed.getX(), seed.getY(), seed.getZ()));
    }

    /**
     * The same, for a caller that has already found the structure.
     *
     * <p>Worth having: the Controller's refresh finds the structure and then wants its patterns, and
     * without this it scanned the box twice a second -- up to 4,096 positions each time, every one
     * of them a chunk lookup as well as a block read. Handing the result along is free.
     */
    public static StructurePatterns of(final Level level, final Result result) {
        if (!result.formed()) {
            return new StructurePatterns(List.of());
        }
        final List<PatternStorageBlockEntity> found = new ArrayList<>();
        // The interior only: a Pattern Storage cannot be anywhere else and this avoids walking the
        // shell, which for a 16x16x16 is 1,352 positions that can never contain one.
        for (int x = result.minX() + 1; x <= result.maxX() - 1; x++) {
            for (int y = result.minY() + 1; y <= result.maxY() - 1; y++) {
                for (int z = result.minZ() + 1; z <= result.maxZ() - 1; z++) {
                    if (level.getBlockEntity(new BlockPos(x, y, z))
                        instanceof PatternStorageBlockEntity storage) {
                        found.add(storage);
                    }
                }
            }
        }
        final Comparator<BlockPos> byPosition = Comparator.<BlockPos>comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);
        found.sort(Comparator.comparing(PatternStorageBlockEntity::getBlockPos, byPosition));
        return new StructurePatterns(found);
    }

    /** How many Pattern Storage blocks are backing this view. */
    public int storageCount() {
        return this.storages.size();
    }

    /** Whether anything at all needs pushing, so the usual case costs one loop and no work. */
    public boolean hasDirtySlots() {
        for (final PatternStorageBlockEntity storage : this.storages) {
            if (storage.hasDirtySlots()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Visits the slots that have changed, in this view's global numbering, and forgets them.
     *
     * <p>The offsets live here because this is the only thing that knows them -- a storage block
     * knows its own slot 0 but not that it is the structure's slot 54.
     */
    public void drainDirtySlots(final IntConsumer consumer) {
        int offset = 0;
        for (final PatternStorageBlockEntity storage : this.storages) {
            final BitSet dirty = storage.drainDirtySlots();
            for (int local = dirty.nextSetBit(0); local >= 0; local = dirty.nextSetBit(local + 1)) {
                consumer.accept(offset + local);
            }
            offset += storage.patterns().getContainerSize();
        }
    }

    /** Marks everything for pushing, for a node that has just been rebuilt and holds nothing. */
    public void markAllDirty() {
        this.storages.forEach(PatternStorageBlockEntity::markAllDirty);
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.size; i++) {
            if (!this.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(final int slot) {
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        return storage == null ? ItemStack.EMPTY : storage.patterns().getItem(this.localIndex(slot));
    }

    @Override
    public ItemStack removeItem(final int slot, final int amount) {
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        return storage == null
            ? ItemStack.EMPTY
            : storage.patterns().removeItem(this.localIndex(slot), amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        return storage == null
            ? ItemStack.EMPTY
            : storage.patterns().removeItemNoUpdate(this.localIndex(slot));
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        if (storage != null) {
            storage.patterns().setItem(this.localIndex(slot), stack);
        }
    }

    /**
     * Whether a pattern may go in a slot -- answered once per stack, not once per slot.
     *
     * <p><strong>This was 16% of the server thread.</strong> {@code ItemHandlerHelper.insertItem}
     * walks every slot looking for one that will take the item, calling this on each; the filter
     * behind it is RS's {@code PatternProviderItem.isValid}, which parses the pattern. So one
     * shift-click into a 54-slot structure validated the same pattern 54 times, and a structure with
     * several storage blocks multiplied that again.
     *
     * <p>The slot cannot change the answer: {@code FilteredContainer.canPlaceItem} ignores it and
     * tests the stack alone. So the result is remembered for as long as the same stack keeps being
     * offered, which is exactly the duration of one insert.
     *
     * <p><strong>Compared by identity, deliberately.</strong> {@code ItemStack} has no meaningful
     * {@code equals}, so a value comparison is not available -- and identity is what is wanted here
     * anyway: the guarantee being relied on is that {@code insertItem} passes the same instance down
     * the loop, and any other instance should be re-checked rather than assumed.
     */
    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        if (stack == this.lastCheckedStack) {
            return this.lastCheckedResult;
        }
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        final boolean result =
            storage != null && storage.patterns().canPlaceItem(this.localIndex(slot), stack);
        this.lastCheckedStack = stack;
        this.lastCheckedResult = result;
        return result;
    }

    @Override
    public void setChanged() {
        this.storages.forEach(PatternStorageBlockEntity::setChanged);
    }

    @Override
    public boolean stillValid(final Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.storages.forEach(storage -> storage.patterns().clearContent());
    }

    @Nullable
    private PatternStorageBlockEntity storageFor(final int slot) {
        return slot >= 0 && slot < this.size ? this.slotToStorage[slot] : null;
    }

    private int localIndex(final int slot) {
        return slot >= 0 && slot < this.size ? this.slotToLocalIndex[slot] : 0;
    }
}

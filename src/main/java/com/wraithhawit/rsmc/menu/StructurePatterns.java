package com.wraithhawit.rsmc.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private StructurePatterns(final List<PatternStorageBlockEntity> storages) {
        this.storages = storages;
        int total = 0;
        for (final PatternStorageBlockEntity storage : storages) {
            total += storage.patterns().getContainerSize();
        }
        this.size = total;
    }

    /**
     * Gathers the Pattern Storage blocks of the structure containing {@code seed}.
     *
     * <p>Empty when the structure is not formed -- a half-built box has no pattern screen, and the
     * player is told why by {@code /rsmc info} rather than by an empty window.
     */
    public static StructurePatterns of(final Level level, final BlockPos seed) {
        final Result result = MultiblockShape.find(
            new LevelBlockSource(level), seed.getX(), seed.getY(), seed.getZ());
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

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        final PatternStorageBlockEntity storage = this.storageFor(slot);
        return storage != null && storage.patterns().canPlaceItem(this.localIndex(slot), stack);
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

    private PatternStorageBlockEntity storageFor(final int slot) {
        int remaining = slot;
        for (final PatternStorageBlockEntity storage : this.storages) {
            final int capacity = storage.patterns().getContainerSize();
            if (remaining < capacity) {
                return storage;
            }
            remaining -= capacity;
        }
        return null;
    }

    private int localIndex(final int slot) {
        int remaining = slot;
        for (final PatternStorageBlockEntity storage : this.storages) {
            final int capacity = storage.patterns().getContainerSize();
            if (remaining < capacity) {
                return remaining;
            }
            remaining -= capacity;
        }
        return 0;
    }
}

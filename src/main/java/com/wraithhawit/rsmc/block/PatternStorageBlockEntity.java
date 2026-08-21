package com.wraithhawit.rsmc.block;

import com.refinedmods.refinedstorage.common.autocrafting.PatternInventory;
import com.refinedmods.refinedstorage.common.support.BlockEntityWithDrops;
import com.refinedmods.refinedstorage.common.util.ContainerUtil;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.StructurePower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds patterns. This is where they live, and the reason the mod is reliable.
 *
 * <p>Reborn Storage keeps its pattern inventories on the multiblock controller -- an object whose
 * belief about the structure can drift from the blocks that are actually there. When the thing that
 * can drift is also the thing holding your patterns, a desync is data loss.
 *
 * <p>Here a pattern is in a block, the block is in the world, and breaking the structure moves
 * nothing. There is no moment at which patterns are in flight between owners, and nothing owns them
 * that can fail to exist.
 *
 * <p><strong>Breaking one takes its patterns with it</strong>, dropped like the contents of any
 * container. That is the intended behaviour rather than a leak: the alternative is patterns
 * outliving the block they were in, which is exactly the ownership question this design exists to
 * avoid having.
 *
 * <p>The inventory is Refined Storage's own {@link PatternInventory}, so a slot accepts what RS
 * considers a pattern and nothing else -- no separate idea of validity to drift from theirs.
 */
public class PatternStorageBlockEntity extends BlockEntity implements BlockEntityWithDrops {
    private static final String TAG_PATTERNS = "patterns";

    private final PatternInventory patterns =
        new PatternInventory(StructurePower.PATTERNS_PER_STORAGE, this::getLevel);

    public PatternStorageBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.PATTERN_STORAGE.get(), pos, state);
    }

    /** The slots this block contributes to the structure's pattern screen. */
    public PatternInventory patterns() {
        return this.patterns;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_PATTERNS, ContainerUtil.write(this.patterns, registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_PATTERNS)) {
            ContainerUtil.read(tag.getCompound(TAG_PATTERNS), this.patterns, registries);
        }
    }

    @Override
    public NonNullList<ItemStack> getDrops() {
        final NonNullList<ItemStack> drops = NonNullList.create();
        for (int i = 0; i < this.patterns.getContainerSize(); i++) {
            final ItemStack stack = this.patterns.getItem(i);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }
}

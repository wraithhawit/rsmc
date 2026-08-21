package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.structure.StructureBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds patterns. Goes in the interior, alongside the CPUs.
 *
 * <p>Unlike a CPU this does carry a block entity, because it carries the patterns themselves --
 * and where those live is the whole reliability argument for this mod. Reborn Storage keeps the
 * pattern inventories on the multiblock controller, an object whose belief about the structure can
 * drift out of step with the world; when the drifting object holds your patterns, a desync is data
 * loss.
 *
 * <p>Here the patterns are in the block. Break the structure and they have not gone anywhere,
 * because they were never anywhere else. Nothing owns them that can fail to exist.
 */
public class PatternStorageBlock extends Block implements EntityBlock, StructureBlock {
    private static final MultiblockShape.Component COMPONENT =
        MultiblockShape.Component.of(BlockKind.PATTERN_STORAGE);

    public PatternStorageBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public MultiblockShape.Component component() {
        return COMPONENT;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return RsmcBlockEntities.PATTERN_STORAGE.get().create(pos, state);
    }

    /**
     * Drops the patterns when the block goes.
     *
     * <p>The loot table cannot do this -- it drops the block, and Refined Storage's own container
     * blocks do exactly the same thing and handle their contents here instead
     * ({@code AbstractBaseBlock.onRemove}). Copied rather than inherited because inheriting would
     * mean extending an RS block class and taking its menu, naming and configuration-card
     * behaviour with it.
     *
     * <p>The guard matters: without the block-changed check this fires on every block state change,
     * and the patterns would be dropped by the structure simply lighting up.
     */
    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moved) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof PatternStorageBlockEntity patternStorage) {
            Containers.dropContents(level, pos, patternStorage.getDrops());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}

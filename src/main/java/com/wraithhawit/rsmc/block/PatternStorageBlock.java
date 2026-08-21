package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.structure.StructureBlock;

import net.minecraft.core.BlockPos;
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
}

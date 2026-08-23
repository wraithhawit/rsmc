package com.wraithhawit.rsmc.block;

import com.wraithhawit.rsmc.structure.StructureChanges;
import javax.annotation.Nullable;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.menu.PatternScreenOpener;
import com.wraithhawit.rsmc.structure.StructureBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
        StructureChanges.bump();
    }

    /**
     * Right-clicking opens the structure's pattern screen. Every block in the structure does this;
     * see {@link com.wraithhawit.rsmc.menu.PatternScreenOpener}.
     */
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level,
                                               final BlockPos pos, final Player player,
                                               final BlockHitResult hit) {
        return PatternScreenOpener.open(level, pos, player);
    }

    /**
     * Structure geometry changed somewhere. See {@link StructureChanges} for why one global
     * counter is the right amount of machinery here.
     *
     * <p>Hooked on {@code onPlace}/{@code onRemove} rather than a player-facing event because
     * these fire for every cause -- pistons, commands, other mods, world edit -- and a structure
     * broken by a piston is exactly the case a player-event hook would miss.
     */
    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        StructureChanges.bump();
    }
}

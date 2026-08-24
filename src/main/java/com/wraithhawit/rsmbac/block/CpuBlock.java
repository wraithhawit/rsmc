package com.wraithhawit.rsmbac.block;

import com.wraithhawit.rsmbac.structure.StructureChanges;
import com.wraithhawit.rsmbac.structure.CpuTier;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.menu.PatternScreenOpener;
import com.wraithhawit.rsmbac.structure.StructureBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Crafting CPU. Goes in the interior, and adds its tier's weight to the structure's steps/tick.
 *
 * <p>No block entity, on purpose. A CPU has no state beyond its tier, and its tier is which block
 * it is -- so there is nothing to store, nothing to save, and nothing to tick. The interior of a
 * formed structure is also sealed, so a CPU never needs to reach the network or be clicked; the
 * shell handles both.
 *
 * <p>That matters at the top end. A 16x16x16 has a 14x14x14 interior of 2,744 positions, and every
 * one of them being a block entity would be a real cost for storing a number that the block
 * registry already knows.
 */
public class CpuBlock extends Block implements StructureBlock {
    private final CpuTier tier;
    private final MultiblockShape.Component component;

    public CpuBlock(final Properties properties, final CpuTier tier) {
        super(properties);
        this.tier = tier;
        this.component = MultiblockShape.Component.cpu(tier);
    }

    public CpuTier tier() {
        return this.tier;
    }

    @Override
    public MultiblockShape.Component component() {
        return this.component;
    }

    /**
     * Right-clicking opens the structure's pattern screen. Every block in the structure does this;
     * see {@link com.wraithhawit.rsmbac.menu.PatternScreenOpener}.
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

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        StructureChanges.bump();
    }
}

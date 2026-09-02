package com.wraithhawit.rsmbac.block;

import com.wraithhawit.rsmbac.structure.StructureChanges;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmbac.menu.PatternScreenOpener;
import com.wraithhawit.rsmbac.structure.StructureBlock;

import javax.annotation.Nullable;

import com.wraithhawit.rsmbac.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The two plain shell blocks: the Frame on the edges and corners, the Casing on the wall panels.
 *
 * <p>One class for both, because they differ in exactly one thing -- which position in the box they
 * are allowed to occupy -- and that difference is a value, not behaviour. {@link PortBlock} is a
 * third, and extends this for the same reason: it is a wall block that happens to also be an
 * inventory.
 *
 * <h2>They do carry a block entity, and it is the smallest one that works</h2>
 *
 * <p>Refined Storage walks its network graph outgoing-only, so a cable probes the position next to
 * it and finds nothing unless a container lives exactly there. Cabling the structure from any face
 * therefore means a block entity and a network node on every shell block; a 16x16x16 pays 2,168 of
 * each. There is no way around it, and see {@link ShellBlockEntity} for why it is nevertheless
 * cheap -- which is that none of it is per tick.
 *
 * <p>What the Controller is the structure's single point of is the <em>pattern provider</em>: one
 * node that crafts, one GUI, one energy draw. These are doorways and nothing else.
 */
public class ShellBlock extends Block implements EntityBlock, StructureBlock {
    private final MultiblockShape.Component component;

    public ShellBlock(final Properties properties, final BlockKind kind) {
        super(properties);
        this.component = MultiblockShape.Component.of(kind);
    }

    @Override
    public MultiblockShape.Component component() {
        return this.component;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return RsmcBlockEntities.SHELL.get().create(pos, state);
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

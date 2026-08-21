package com.wraithhawit.rsmc.block;

import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.structure.StructureBlock;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two plain shell blocks: the Frame on the edges and corners, the Casing on the wall panels.
 *
 * <p>One class for both, because they differ in exactly one thing -- which position in the box they
 * are allowed to occupy -- and that difference is a value, not behaviour.
 *
 * <h2>No block entity, and that is the point of the Controller</h2>
 *
 * <p>These briefly had one. Refined Storage walks its network graph outgoing-only, so a cable
 * probes the position next to it and finds nothing unless a container lives exactly there -- which
 * meant every shell block needed a block entity and a network node just so the structure could be
 * cabled from any face. A 16x16x16 paid 2,168 of each.
 *
 * <p>The Controller replaces all of that with one. It is the structure's single point of contact:
 * one block entity, one network node, one place a cable attaches. The shell went back to being
 * plain blocks, which is what it always was in everything but bookkeeping.
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
}

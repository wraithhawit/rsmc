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
 * The two blocks that make up the outer shell: the Frame on the edges and corners, and the Casing
 * on the flat wall panels.
 *
 * <p>One class for both, because they differ in exactly one thing -- which position in the box they
 * are allowed to occupy -- and that difference is a value, not behaviour. Two nearly identical
 * classes would be two places to change every time the shell gains anything.
 *
 * <h2>Why these carry a block entity and the CPU does not</h2>
 *
 * <p>The shell is the part of the structure that touches the outside world, so it is the part that
 * has to reach the Refined Storage network and the part a player can click. The interior is sealed
 * by definition -- every face of an interior block is against another block of the structure -- so
 * a CPU needs no block entity at all, and a 16x16x16 built mostly of CPUs costs 2,168 block
 * entities for its shell rather than 4,096 for everything.
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

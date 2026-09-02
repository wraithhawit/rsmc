package com.wraithhawit.rsmbac.block;

import javax.annotation.Nullable;

import com.wraithhawit.rsmbac.content.RsmcBlockEntities;
import com.wraithhawit.rsmbac.structure.MultiblockShape.BlockKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Pattern Port: a wall panel that patterns can be piped into.
 *
 * <p>A {@link ShellBlock} in every respect that the structure cares about -- it fills a wall slot,
 * it relays a network connection, right-clicking it opens the pattern screen -- with one addition,
 * which lives in {@link PortBlockEntity}: an item handler.
 *
 * <h2>Why this block has to exist</h2>
 *
 * <p>Patterns live in Pattern Storage blocks, and those are <em>interior</em> by the shape rules,
 * meaning every one of their six faces touches another structure block. No pipe, hopper, exporter
 * or anything else can ever be adjacent to one. Exposing an inventory on them would be exposing it
 * where nothing can reach.
 *
 * <p>So insertion has to arrive at a wall block and be routed inward. The Controller could have
 * carried that, and needs no new block -- but its outward face is a screen showing what the
 * structure is doing, and a pipe stuck to it hides the one thing it is there to show.
 *
 * <p>Any number per structure, including none: a structure with no Port is exactly what every
 * structure was before this block existed.
 *
 * <p>The idea, and the two-virtual-slot shape of the handler, is Reborn Storage's {@code TileIoPort}
 * -- see {@link PortBlockEntity}. The art is not; {@code port.png} is generated from our own Casing
 * by {@code tools/GenerateTextures.java}.
 */
public class PortBlock extends ShellBlock {
    public PortBlock(final Properties properties) {
        super(properties, BlockKind.PORT);
    }

    /**
     * Its own block entity type, and that is the whole reason this is a separate class.
     *
     * <p>Capabilities are registered per block entity <em>type</em>. Reusing the shell's type would
     * put an item handler on every Frame and Casing in the structure -- thousands of them, each
     * advertising an inventory that any importer or external storage in the pack is entitled to go
     * looking through.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return RsmcBlockEntities.PORT.get().create(pos, state);
    }
}

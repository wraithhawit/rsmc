package com.wraithhawit.rsmbac.structure;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The one place the level and the shape code meet.
 *
 * <p>{@link MultiblockShape} holds no Minecraft types so that it can be tested in a plain JVM; this
 * is the adapter that lets it read a real world. Nothing else should implement
 * {@link MultiblockShape.BlockSource} outside of tests.
 */
public record LevelBlockSource(LevelReader level) implements MultiblockShape.BlockSource {
    @Nullable
    @Override
    public MultiblockShape.Component blockAt(final int x, final int y, final int z) {
        final BlockPos pos = new BlockPos(x, y, z);
        // An unloaded chunk reads as "not ours", never as air-that-might-be-something-later.
        //
        // getBlockState on an unloaded position does not load the chunk -- it answers air -- so
        // without this check a structure straddling a chunk border would be found to have a hole
        // in it, form differently depending on which chunks happened to be loaded, and change
        // shape underneath itself when the neighbour loaded. Refusing to answer is the only
        // stable option: a structure that cannot be seen in full does not form.
        //
        // Asked as "give me this chunk but do not create it", because both convenience helpers
        // for the question -- hasChunkAt(BlockPos) and hasChunk(int, int) -- are deprecated. The
        // false is the load flag and is the entire point: a structure check must never be the
        // thing that drags a chunk into memory.
        if (this.level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) {
            return null;
        }
        return this.level.getBlockState(pos).getBlock() instanceof StructureBlock structureBlock
            ? structureBlock.component()
            : null;
    }
}

package com.wraithhawit.rsmc.structure;

/**
 * Implemented by every block that can be part of the multiblock.
 *
 * <p>This is the only thing that decides whether a block in the world counts. Deliberately an
 * interface on the block rather than a tag or a list held somewhere: a block that is part of the
 * structure knows which role it fills, and a lookup table that has to be kept in step with the
 * block registry is a second source of truth waiting to disagree with the first.
 *
 * <p>It is also what keeps {@link MultiblockShape} free of Minecraft types. The shape code asks
 * {@link MultiblockShape.BlockSource} for a {@link MultiblockShape.Component}; the adapter that
 * reads the level is the only place the two worlds meet.
 */
public interface StructureBlock {
    /**
     * @return what this block contributes to a structure -- its kind, and its tier if it has one
     */
    MultiblockShape.Component component();
}

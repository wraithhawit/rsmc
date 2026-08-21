package com.wraithhawit.rsmc.block;

import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.StructureBlock;

import net.minecraft.world.level.block.Block;

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
}

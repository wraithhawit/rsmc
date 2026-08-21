package com.wraithhawit.rsmc.block;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the Frame and Casing blocks -- the shell.
 *
 * <p>Empty today. It exists now because the shell is what will reach the Refined Storage network
 * (#2) and what a player clicks to open the GUI (#4), and both want a block entity at every shell
 * position; adding it later would mean every existing structure in every world losing and
 * recreating its shell entities on update.
 *
 * <p><strong>It will stay stateless about the structure.</strong> Nothing here caches whether the
 * multiblock is formed, how big it is, or where its corner is. That is the decision the whole mod
 * rests on: the structure is recomputed from the world by
 * {@link com.wraithhawit.rsmc.structure.MultiblockShape#find}, never remembered, so there is no
 * stored belief that can disagree with the blocks that are actually there. If a future change wants
 * to cache the formed structure here for speed, that is the mistake this design exists to avoid --
 * measure first, and read the reasoning in the README before deciding it is worth it.
 */
public class ShellBlockEntity extends BlockEntity {
    public ShellBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.SHELL.get(), pos, state);
    }
}

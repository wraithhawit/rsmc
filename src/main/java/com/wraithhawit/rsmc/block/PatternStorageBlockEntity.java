package com.wraithhawit.rsmc.block;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for a Pattern Storage block. This is where patterns live.
 *
 * <p>The inventory itself lands with the GUI (#4) and the network node (#2); what is settled now is
 * <em>where</em> it goes, which is here and nowhere else. A pattern is in a block, the block is in
 * the world, and breaking the structure moves nothing -- so there is no state to reconcile and no
 * moment at which patterns are in flight between owners.
 *
 * <p>Two consequences to build to when the inventory arrives:
 *
 * <ul>
 *   <li>Breaking a Pattern Storage block takes its patterns with it. That is the intended
 *       behaviour, not a leak: they drop with the block, the way the contents of any container do.
 *   <li>The GUI is a <em>view</em> assembled over the Pattern Storage blocks in the structure, not
 *       a container that owns them. Adding a block adds a page.
 * </ul>
 */
public class PatternStorageBlockEntity extends BlockEntity {
    public PatternStorageBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.PATTERN_STORAGE.get(), pos, state);
    }
}

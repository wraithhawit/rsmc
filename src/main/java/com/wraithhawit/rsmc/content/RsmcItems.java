package com.wraithhawit.rsmc.content;

import java.util.ArrayList;
import java.util.List;

import com.wraithhawit.rsmc.RSMC;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The block items. rsmc adds no items of its own -- everything it registers is something you place.
 */
public final class RsmcItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RSMC.MODID);
    /**
     * In the same order as {@link RsmcBlocks#all()}, which is the order they appear in the creative
     * tab. Built by walking the block registry rather than listed again here: a block without an
     * item is a block you cannot obtain, and the only way to be sure that never happens is to not
     * have a second list that can fall out of step.
     */
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    static {
        for (final DeferredBlock<? extends Block> block : RsmcBlocks.all()) {
            BLOCK_ITEMS.add(ITEMS.register(
                block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    private RsmcItems() {
    }
}

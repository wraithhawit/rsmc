package com.wraithhawit.rsmc.content;

import com.wraithhawit.rsmc.RSMC;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One tab holding all seven blocks, in build order: shell first, then what goes inside it. */
public final class RsmcCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RSMC.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + RSMC.MODID))
            .icon(() -> new ItemStack(RsmcBlocks.FRAME.get()))
            .displayItems((parameters, output) ->
                RsmcItems.BLOCK_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private RsmcCreativeTab() {
    }
}

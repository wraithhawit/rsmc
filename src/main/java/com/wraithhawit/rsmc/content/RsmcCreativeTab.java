package com.wraithhawit.rsmc.content;

import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;

import com.wraithhawit.rsmc.RSMC;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One tab holding all seven blocks, in build order: shell first, then what goes inside it.
 *
 * <p><strong>Placed immediately after Refined Storage's own tab</strong>, rather than left to land
 * wherever registration order puts it. In a pack the size of ATM10 the creative menu runs to
 * something like 28 pages, and a tab appended to the end is a tab nobody finds; next to RS is where
 * someone looking for an RS addon will actually look.
 *
 * <p>The id is asked of Refined Storage rather than written out as a literal, so it cannot drift if
 * they rename it. Safe to call here: rsmc declares {@code ordering="AFTER"} on refinedstorage, so
 * RS's mod constructor -- which installs the API delegate -- has already run by the time any
 * registry event fires.
 */
public final class RsmcCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RSMC.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + RSMC.MODID))
            .icon(() -> new ItemStack(RsmcBlocks.FRAME.get()))
            .withTabsAfter(RefinedStorageApi.INSTANCE.getCreativeModeTabId())
            .displayItems((parameters, output) ->
                RsmcItems.BLOCK_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private RsmcCreativeTab() {
    }
}

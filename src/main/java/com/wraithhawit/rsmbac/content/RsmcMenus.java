package com.wraithhawit.rsmbac.content;

import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.menu.PatternMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The pattern screen's menu type.
 *
 * <p>Created with {@link IMenuTypeExtension#create}, the extended-data form, because the client has
 * to be told how many pattern slots the structure has before it can build the menu. It cannot work
 * that out for itself: the count comes from how many Pattern Storage blocks are inside a box the
 * client has no reason to have scanned.
 *
 * <p>The count is the <em>only</em> thing sent. Everything else -- which patterns are in which slot
 * -- arrives through vanilla's ordinary slot syncing, which already does that job correctly.
 */
public final class RsmcMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, RSMBAC.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PatternMenu>> PATTERNS =
        MENUS.register("patterns", () -> IMenuTypeExtension.create(PatternMenu::new));

    private RsmcMenus() {
    }
}

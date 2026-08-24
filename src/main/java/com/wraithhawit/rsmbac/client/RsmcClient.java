package com.wraithhawit.rsmbac.client;

import com.wraithhawit.rsmbac.content.RsmcMenus;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-only wiring: the pattern menu needs a screen to open into. */
public final class RsmcClient {
    private RsmcClient() {
    }

    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(RsmcMenus.PATTERNS.get(), PatternScreen::new);
    }

    /**
     * Installs the highlight handler and its render/tick hooks.
     *
     * <p>Client setup rather than the payload registration, so no client class is ever named
     * from code a dedicated server loads -- see {@code ClientHighlightHandler}.
     */
    public static void setup(final FMLClientSetupEvent event) {
        event.enqueueWork(StructureHighlight::install);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, StructureHighlight::render);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> StructureHighlight.tick());
    }
}

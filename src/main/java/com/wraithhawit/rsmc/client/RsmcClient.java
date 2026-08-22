package com.wraithhawit.rsmc.client;

import com.wraithhawit.rsmc.content.RsmcMenus;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring: the pattern menu needs a screen to open into. */
public final class RsmcClient {
    private RsmcClient() {
    }

    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(RsmcMenus.PATTERNS.get(), PatternScreen::new);
    }
}

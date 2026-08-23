package com.wraithhawit.rsmc.network;

import com.wraithhawit.rsmc.RSMC;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's only packet. Registered as optional so a client without rsmc can still connect --
 * everything the mod does is server-authoritative, and the highlight is a convenience.
 */
public final class RsmcPayloads {
    private RsmcPayloads() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(
            HighlightBlockPayload.TYPE,
            HighlightBlockPayload.CODEC,
            // The handler lives in a client-only class, reached through a holder so this class
            // stays loadable on a dedicated server. Referencing a client class directly here --
            // even inside a lambda -- puts it in this class's constant pool and crashes the
            // server at class load.
            (payload, context) -> context.enqueueWork(
                () -> ClientHighlightHandler.HANDLER.accept(payload)));
    }

    /** Send it. No-op for anything that is not a real connected player. */
    public static void highlight(final ServerPlayer player, final HighlightBlockPayload payload) {
        player.connection.send(payload);
    }
}

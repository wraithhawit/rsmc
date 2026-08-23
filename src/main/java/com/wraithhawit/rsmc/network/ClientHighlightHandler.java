package com.wraithhawit.rsmc.network;

import java.util.function.Consumer;

/**
 * The seam that keeps a client-only class out of the packet registration.
 *
 * <p>{@link RsmcPayloads} runs on both sides. If its handler lambda named a client class
 * directly, that class would land in {@code RsmcPayloads}' constant pool and a dedicated server
 * would fail to load it -- a crash that never shows up in single-player and always shows up for
 * the first person to run a server.
 *
 * <p>So the handler is a field, defaulted to doing nothing, and the client swaps in the real one
 * during its own setup.
 */
public final class ClientHighlightHandler {
    /** Replaced on the client. On a dedicated server it stays a no-op and is never called. */
    public static volatile Consumer<HighlightBlockPayload> HANDLER = payload -> { };

    private ClientHighlightHandler() {
    }
}

package com.wraithhawit.rsmc.content;

import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import com.wraithhawit.rsmc.block.ShellBlockEntity;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the shell block entities to Refined Storage.
 *
 * <p>RS finds the node containers at a position through a NeoForge block capability --
 * {@code PlatformImpl.getContainerProviderSafely} resolves
 * {@code getNetworkNodeContainerProviderCapability()} against the block entity there -- so
 * registering it is what makes a cable touching the structure see anything at all. Without this the
 * shell block entities exist, hold nodes, and are invisible to the network.
 */
public final class RsmcCapabilities {
    private RsmcCapabilities() {
    }

    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.SHELL.get(),
            (blockEntity, direction) -> ((ShellBlockEntity) blockEntity).containerProvider());
    }
}

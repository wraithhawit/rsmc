package com.wraithhawit.rsmbac.content;

import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.ShellBlockEntity;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Controller and the shell blocks to Refined Storage.
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
        // Both, and for different reasons: the Controller because it hosts the real node, the shell
        // because a cable has to be able to touch any face of the box and find something there.
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.SHELL.get(),
            (blockEntity, direction) -> ((ShellBlockEntity) blockEntity).containerProvider());
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.CONTROLLER.get(),
            (blockEntity, direction) -> ((ControllerBlockEntity) blockEntity).containerProvider());
    }
}

package com.wraithhawit.rsmbac.content;

import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.PortBlockEntity;
import com.wraithhawit.rsmbac.block.ShellBlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the structure to Refined Storage, and the Pattern Port to everything else.
 *
 * <p>RS finds the node containers at a position through a NeoForge block capability --
 * {@code PlatformImpl.getContainerProviderSafely} resolves
 * {@code getNetworkNodeContainerProviderCapability()} against the block entity there -- so
 * registering it is what makes a cable touching the structure see anything at all. Without this the
 * shell block entities exist, hold nodes, and are invisible to the network.
 *
 * <p>The item handler on the Port is the same idea in the other direction: the block entity has
 * implemented {@link net.neoforged.neoforge.items.IItemHandler} the whole time, and this line is
 * the only thing that lets a hopper find out.
 */
public final class RsmcCapabilities {
    private RsmcCapabilities() {
    }

    public static void register(final RegisterCapabilitiesEvent event) {
        // All three, for different reasons: the Controller because it hosts the real node, the
        // shell because a cable has to be able to touch any face of the box and find something
        // there, and the Port because it is a shell block that happens to have its own type.
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.SHELL.get(),
            (blockEntity, direction) -> ((ShellBlockEntity) blockEntity).containerProvider());
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.CONTROLLER.get(),
            (blockEntity, direction) -> ((ControllerBlockEntity) blockEntity).containerProvider());
        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            RsmcBlockEntities.PORT.get(),
            (blockEntity, direction) -> ((PortBlockEntity) blockEntity).containerProvider());

        // What makes patterns pipeable, and the reason the Port has a block entity type to itself.
        // The blocks that actually hold patterns are interior, so an inventory on them would be
        // somewhere nothing can reach; an inventory on the shell type would be thousands of blocks
        // all advertising the same contents.
        //
        // Side-independent on purpose. A wall block has exactly one face that is not buried in the
        // structure, so filtering by direction could only ever be a way to get that face wrong.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            RsmcBlockEntities.PORT.get(),
            (blockEntity, direction) -> (PortBlockEntity) blockEntity);
    }
}

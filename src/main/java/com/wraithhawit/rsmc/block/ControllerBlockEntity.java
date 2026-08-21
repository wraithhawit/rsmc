package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one block entity the structure has, and the one network node.
 *
 * <p>Everything the structure needs from the outside world happens here: it joins the Refined
 * Storage network, and it will host the pattern provider once #2 step 2 lands.
 *
 * <h2>Still stateless about the structure</h2>
 *
 * <p>Nothing here caches whether the multiblock is formed, how big it is, or what is inside it.
 * The structure is recomputed from the world by
 * {@link com.wraithhawit.rsmc.structure.MultiblockShape#find} whenever it is needed.
 *
 * <p>This is the exact spot where Reborn Storage's design goes wrong, so it is worth naming: their
 * controller is a persistent object with an assembly state machine and the pattern inventories on
 * it, and its belief about the structure can drift from the blocks that are actually there. Having
 * a controller <em>block</em> is not the same mistake as having a controller <em>object that
 * remembers</em> -- but it is the thing that invites it. If a future change wants to cache the
 * formed structure in this class, that is the mistake the whole design exists to avoid.
 */
public class ControllerBlockEntity extends BlockEntity {
    /**
     * Zero for now. The whole structure's draw gets charged here once the block counts feed into it
     * -- the interior blocks have no block entity of their own, so there is nowhere else it could
     * go, and that is deliberate rather than a limitation.
     */
    private final SimpleNetworkNode node = new SimpleNetworkNode(0L);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    public ControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.CONTROLLER.get(), pos, state);
    }

    /**
     * Built lazily rather than in the constructor: block entities are constructed during chunk
     * load, before the level is set, and the container wants a block entity that already knows
     * where it is.
     */
    public NetworkNodeContainerProvider containerProvider() {
        if (this.containerProvider == null) {
            final NetworkNodeContainerProvider provider =
                RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
            final InWorldNetworkNodeContainer container = RefinedStorageApi.INSTANCE
                .createNetworkNodeContainer(this, this.node)
                .name("controller")
                .build();
            provider.addContainer(container);
            this.containerProvider = provider;
        }
        return this.containerProvider;
    }

    public SimpleNetworkNode node() {
        return this.node;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.containerProvider != null) {
            this.containerProvider.remove(this.level);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.initializeIfServer();
    }

    /**
     * Joins the network. Server only -- the client has no network graph, and asking for one there
     * walks into RS internals that only exist server-side.
     */
    public void initializeIfServer() {
        final Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }
        this.containerProvider().initialize(currentLevel, null);
    }
}

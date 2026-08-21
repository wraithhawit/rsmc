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
 * Block entity for the Frame and Casing blocks -- the shell.
 *
 * <p>Its job is connectivity: it puts a Refined Storage network node container at this position so
 * that a cable touching <em>any</em> face of the structure joins it to the network.
 *
 * <h2>Why every shell block needs one, rather than one node for the whole structure</h2>
 *
 * <p>RS builds its network graph outgoing-only. {@code ConnectionProviderImpl.findConnectionsAt}
 * asks a container where it reaches and then looks for containers <em>at those positions</em>; a
 * cable's reach is its six neighbours. So when a cable sits against one of our wall blocks it
 * probes that wall position, and if nothing lives there the walk simply never arrives. Our own
 * outgoing connections cannot help, because nothing ever gets to us to ask for them.
 *
 * <p>A single node at the corner would therefore connect at the corner and nowhere else. This is
 * the same thing an RS cable is, and the same cost RS pays for cable: 98 of them for a 5x5x5, 2,168
 * for a 16x16x16. The interior contributes none -- a sealed box cannot be probed from outside,
 * which is the other half of why a CPU has no block entity at all.
 *
 * <h2>Still stateless about the structure</h2>
 *
 * <p>Nothing here caches whether the multiblock is formed, how big it is, or where its corner is.
 * The node this holds is a connector and knows nothing; the structure is recomputed from the world
 * by {@link com.wraithhawit.rsmc.structure.MultiblockShape#find}. If a future change wants to cache
 * the formed structure here for speed, that is the mistake the design exists to avoid.
 */
public class ShellBlockEntity extends BlockEntity {
    /**
     * Zero energy, deliberately. The whole structure's draw is charged once on the pattern provider
     * node, computed from the block counts the shape code already returns -- because the interior
     * blocks have no block entity and so no node to charge, and a per-node split would leave half
     * the structure running for free.
     */
    private final SimpleNetworkNode node = new SimpleNetworkNode(0L);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    public ShellBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.SHELL.get(), pos, state);
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
                .name("shell")
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

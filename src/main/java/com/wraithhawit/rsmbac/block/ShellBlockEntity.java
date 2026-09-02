package com.wraithhawit.rsmbac.block;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import com.wraithhawit.rsmbac.content.RsmcBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A connection relay: it exists so a cable touching <em>any</em> face of the structure joins it.
 *
 * <p>It does nothing else. No ticker, no saved data, no energy, no knowledge of the structure. The
 * Controller remains the single host of the pattern provider, the GUI and the energy draw; this is
 * only a doorway.
 *
 * <h2>Why RS forces this, and why it is nevertheless cheap</h2>
 *
 * <p>RS walks its network graph outgoing-only, and a cable's reach is its six neighbours -- so a
 * cable against one of our wall blocks probes <em>that</em> position, and finds nothing unless a
 * container lives exactly there. There is no way around it: {@code getContainerProviderSafely}
 * resolves the capability against the block entity at the position and returns null when there is
 * none. Connecting from any face therefore means a block entity on every shell block.
 *
 * <p>That sounds expensive and mostly is not, because <strong>none of it is per tick</strong>. RS
 * ticks a node only through a {@code BlockEntityTicker} that the block itself opts into -- see
 * {@code AutocrafterBlock}, which declares one -- and this block declares none. RS's own server
 * tick handler is a queued-action drain that never iterates the network's nodes. So the real costs
 * are one small object per shell block in memory, and a network rebuild scan proportional to
 * container count that runs when blocks change rather than continuously. Placing a 16x16x16 is
 * already thousands of block updates.
 *
 * <p>The alternatives were both worse: cabling only to the Controller is jank, and a dedicated
 * block just to be cabled to is a whole extra block and shape rule bought to save something that
 * was never costing anything.
 *
 * <p><strong>That last line is about cables, and only cables.</strong> rsmbac does now have a
 * dedicated wall block -- the Pattern Port -- but it is not the block this paragraph turned down.
 * It exists because Pattern Storage blocks are interior, so no pipe can ever touch one; that is a
 * reachability problem with no other answer, where connectivity had a free one.
 */
public class ShellBlockEntity extends BlockEntity {
    /**
     * Zero energy, and the reason matters. The structure's whole draw is charged once on the
     * Controller, computed from the block counts the shape code returns -- so these contributing
     * nothing is what keeps a bigger structure from being cheaper per block than a small one just
     * because it has proportionally more shell.
     */
    private final SimpleNetworkNode node = new SimpleNetworkNode(0L);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    public ShellBlockEntity(final BlockPos pos, final BlockState state) {
        this(RsmcBlockEntities.SHELL.get(), pos, state);
    }

    /**
     * For a shell block that is more than a doorway.
     *
     * <p>{@link PortBlockEntity} is one: it wants everything here -- the relay node, the join, the
     * removal -- and adds an item handler on top. Subclassing rather than copying means the network
     * half cannot drift between the two.
     */
    protected ShellBlockEntity(final BlockEntityType<?> type, final BlockPos pos,
                               final BlockState state) {
        super(type, pos, state);
    }

    /** What this container is called in Refined Storage's network graph. */
    protected String containerName() {
        return "shell";
    }

    /**
     * Built lazily rather than in the constructor: block entities are constructed during chunk
     * load, before the level is set, and the container wants one that already knows where it is.
     */
    public NetworkNodeContainerProvider containerProvider() {
        if (this.containerProvider == null) {
            final NetworkNodeContainerProvider provider =
                RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
            final InWorldNetworkNodeContainer container = RefinedStorageApi.INSTANCE
                .createNetworkNodeContainer(this, this.node)
                .name(this.containerName())
                .build();
            provider.addContainer(container);
            this.containerProvider = provider;
        }
        return this.containerProvider;
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
        final Level currentLevel = this.level;
        // Server only -- the client has no network graph.
        if (currentLevel != null && !currentLevel.isClientSide()) {
            this.containerProvider().initialize(currentLevel, null);
        }
    }
}

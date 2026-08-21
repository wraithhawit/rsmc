package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.impl.node.SimpleNetworkNode;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;
import com.wraithhawit.rsmc.structure.StructurePower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one block entity that does anything, and the structure's only real network node.
 *
 * <p>It joins the Refined Storage network, keeps the screen honest, and will host the pattern
 * provider once #2 step 2 lands.
 *
 * <h2>Still stateless about the structure</h2>
 *
 * <p>Nothing here caches whether the multiblock is formed, how big it is, or what is inside it. The
 * structure is recomputed from the world by {@link MultiblockShape#find} whenever it is needed --
 * including by the screen refresh below, which throws its answer away every time rather than
 * keeping it.
 *
 * <p>This is the exact spot where Reborn Storage's design goes wrong, so it is worth naming: their
 * controller is a persistent object with an assembly state machine and the pattern inventories on
 * it, and its belief about the structure can drift from the blocks that are actually there. Having
 * a controller <em>block</em> is not the same mistake as having a controller <em>object that
 * remembers</em> -- but it is the thing that invites it. If a future change wants to cache the
 * formed structure in this class, that is the mistake the whole design exists to avoid.
 */
public class ControllerBlockEntity extends BlockEntity {
    /** Once a second. See {@link #refreshStateOccasionally()}. */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    /**
     * Starts at zero and is set from the structure on every refresh -- see
     * {@link com.wraithhawit.rsmc.structure.StructurePower}. The whole structure is charged here
     * because the interior blocks have no block entity of their own, so there is nowhere else it
     * could go.
     *
     * <p>It is not only a cost: it is what makes "is this thing actually powered" answerable at
     * all. A node that draws nothing is satisfied by a network that holds nothing.
     */
    private final SimpleNetworkNode node = new SimpleNetworkNode(0L);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    public ControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.CONTROLLER.get(), pos, state);
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

    /**
     * Keeps the screen truthful: unformed, formed-but-unplugged, or live.
     *
     * <p>Once a second, not every tick. All this decides is which of three pictures to show, and
     * re-reading the structure means walking up to 4,096 positions -- worth doing to keep the
     * feedback honest, not worth doing twenty times a second.
     *
     * <p>Polling at all is a placeholder. #3 replaces it with updates driven by the block changes
     * that can actually alter the answer; until that exists, a slow poll is the version that cannot
     * silently go stale.
     */
    public void refreshStateOccasionally() {
        final Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }
        if (currentLevel.getGameTime() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        final BlockState current = this.getBlockState();
        if (!current.hasProperty(ControllerBlock.STATE)) {
            return;
        }
        final ControllerState wanted = this.computeState(currentLevel);
        if (current.getValue(ControllerBlock.STATE) != wanted) {
            currentLevel.setBlock(this.worldPosition,
                current.setValue(ControllerBlock.STATE, wanted), Block.UPDATE_ALL);
        }
    }

    /**
     * Whether the structure is formed, and whether it is actually running.
     *
     * <p><strong>"Has a network" is not a test of anything.</strong> RS's {@code NetworkBuilderImpl}
     * creates a network for a lone container when there is nothing to merge with, so
     * {@code getNetwork() != null} is true the moment this block initialises, cabled or not. An
     * earlier version used it and the screen lit up for every Controller ever placed.
     *
     * <p>So this asks RS's own question instead, the one {@code calculateActive} asks of every RS
     * machine: is energy required at all, and if so does the network hold at least what this
     * structure draws. A one-node network of our own making stores nothing, so it fails that on its
     * own -- no special case needed for "not really connected", because a network with nothing in
     * it cannot power anything.
     *
     * <p>Which is also why the node's energy usage has to be real. With a draw of zero, "stored >=
     * usage" is true of an empty network too, and the bug comes straight back.
     */
    private ControllerState computeState(final Level currentLevel) {
        final Result result = MultiblockShape.find(new LevelBlockSource(currentLevel),
            this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        if (!result.formed()) {
            return ControllerState.UNFORMED;
        }
        this.node.setEnergyUsage(StructurePower.energyUsage(result));
        return this.hasEnergy() ? ControllerState.ACTIVE : ControllerState.INACTIVE;
    }

    private boolean hasEnergy() {
        if (!RefinedStorageApi.INSTANCE.isEnergyRequired()) {
            // A pack with energy switched off: every RS machine runs regardless, and ours should
            // behave the same way rather than being the one block that still refuses.
            return true;
        }
        final Network network = this.node.getNetwork();
        if (network == null) {
            return false;
        }
        return network.getComponent(EnergyNetworkComponent.class).getStored()
            >= this.node.getEnergyUsage();
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

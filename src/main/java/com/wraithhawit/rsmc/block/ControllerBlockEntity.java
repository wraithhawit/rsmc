package com.wraithhawit.rsmc.block;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import com.wraithhawit.rsmc.content.RsmcBlockEntities;
import com.wraithhawit.rsmc.menu.StructurePatterns;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;
import com.wraithhawit.rsmc.structure.StructurePower;
import com.wraithhawit.rsmc.structure.StructureStepBehavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The structure's brain: its one network node, and the pattern provider Refined Storage crafts
 * through.
 *
 * <h2>Still stateless about the structure</h2>
 *
 * <p>Nothing here caches whether the multiblock is formed, how big it is, or what is inside it. The
 * structure is recomputed by {@link MultiblockShape#find} whenever it is needed, and the answer is
 * thrown away every time. What <em>is</em> kept is what the node was last built for -- its pattern
 * capacity, and the version of the patterns already pushed into it -- which is not a belief about
 * the world but a record of what has already been done to the node.
 *
 * <p>This is the exact spot where Reborn Storage's design goes wrong, so it is worth naming: their
 * controller is a persistent object with an assembly state machine and the pattern inventories on
 * it, and its belief about the structure can drift from the blocks that are actually there. A
 * controller <em>block</em> is not the same mistake as a controller <em>object that remembers</em>
 * -- but it is the thing that invites it.
 */
public class ControllerBlockEntity extends BlockEntity {
    /** Once a second. See {@link #refreshStateOccasionally()}. */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    /**
     * The pattern provider. Rebuilt when the structure's pattern capacity changes, because
     * {@link PatternProviderNetworkNode} fixes its slot count at construction.
     */
    private PatternProviderNetworkNode node = new PatternProviderNetworkNode(0L, 0);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    /** What the current node was built for, so a rebuild happens only when it must. */
    private int builtCapacity;

    /** Sum of the storage blocks' pattern versions when patterns were last pushed. */
    private int pushedPatternsVersion = -1;

    /**
     * Whether the current container has actually joined a network.
     *
     * <p>Not a convenience. {@code clearRemoved()} can run before the block entity has a level, in
     * which case the container is never initialised -- and asking RS to remove a container it never
     * received is a hard crash: {@code NetworkBuilderImpl.remove} validates that the container is
     * present and throws "The removed container should be present in the removed entries, but
     * isn't". This is the only thing that knows whether a remove is legal.
     */
    private boolean joinedNetwork;

    /** So a capacity change queues one rebuild, not one per refresh until it happens. */
    private boolean recreateRequested;

    public ControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(RsmcBlockEntities.CONTROLLER.get(), pos, state);
    }

    /**
     * Built lazily rather than in the constructor: block entities are constructed during chunk
     * load, before the level is set, and the container wants one that already knows where it is.
     */
    public NetworkNodeContainerProvider containerProvider() {
        if (this.containerProvider == null) {
            this.containerProvider = this.buildContainerProvider();
        }
        return this.containerProvider;
    }

    private NetworkNodeContainerProvider buildContainerProvider() {
        final NetworkNodeContainerProvider provider =
            RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        final InWorldNetworkNodeContainer container = RefinedStorageApi.INSTANCE
            .createNetworkNodeContainer(this, this.node)
            .name("controller")
            .build();
        provider.addContainer(container);
        return provider;
    }

    public PatternProviderNetworkNode node() {
        return this.node;
    }

    /**
     * Performs crafting. Every tick, no throttling.
     *
     * <p><strong>This is what makes the structure craft at all.</strong> Refined Storage does not
     * drive a pattern provider from the network -- the provider's own block entity is ticked and
     * calls {@code doWork}, which steps its tasks; {@code NetworkNodeBlockEntityTicker} does exactly
     * this for an autocrafter. Without it the node joins the network, accepts patterns, reports its
     * speed, appears in the crafting preview, and then every craft stalls forever, because nothing
     * ever asks it to take a step.
     *
     * <p>Which is a memorable failure: everything looks correct except that nothing happens.
     */
    public void tickNode() {
        final Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }
        this.node.doWork();
    }

    /**
     * Keeps the node and the screen in step with the blocks that are actually there.
     *
     * <p>Once a second, not every tick: re-reading the structure walks up to 4,096 positions and
     * nothing here needs to be more current than that. RS drives the crafting itself, at the rate
     * {@link StructureStepBehavior} reports, so a slow refresh does not slow crafting down -- it
     * only delays noticing that the structure changed.
     *
     * <p>Polling at all is a placeholder; #3 replaces it with updates driven by the block changes
     * that can actually alter the answer.
     */
    public void refreshStateOccasionally() {
        final Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }
        if (currentLevel.getGameTime() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        final Result result = MultiblockShape.find(new LevelBlockSource(currentLevel),
            this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        this.syncNode(currentLevel, result);
        this.updateScreen(currentLevel, result);
    }

    /**
     * Points the node at what the structure currently is: its capacity, its patterns, its speed and
     * its energy draw.
     */
    private void syncNode(final Level currentLevel, final Result result) {
        if (!result.formed()) {
            // A broken structure crafts nothing, rather than crafting slowly or continuing to craft
            // whatever it was last told about.
            this.node.setStepBehavior(StructureStepBehavior.IDLE);
            this.node.setActive(false);
            return;
        }
        this.node.setEnergyUsage(StructurePower.energyUsage(result));
        final StructurePatterns patterns = StructurePatterns.of(currentLevel, this.worldPosition);
        this.ensureCapacity(currentLevel, patterns.getContainerSize());
        this.pushPatternsIfChanged(currentLevel, patterns);
        final boolean active = this.hasEnergy();
        this.node.setActive(active);
        this.node.setStepBehavior(new StructureStepBehavior(result.stepsPerTick(), active));
    }

    /**
     * Rebuilds the node when the structure gains or loses pattern capacity.
     *
     * <p>{@link PatternProviderNetworkNode} takes its slot count in the constructor and never
     * changes it, so adding a Pattern Storage block means a new node -- and a new container, and
     * rejoining the network.
     *
     * <p><strong>Nothing is lost in the swap</strong>, which is the payoff for keeping patterns in
     * the blocks rather than on the node: they are still sitting in the Pattern Storage block
     * entities and get pushed into the new node immediately afterwards. Had they lived on the node,
     * this would be a migration, with somewhere for them to fall out.
     */
    private void ensureCapacity(final Level currentLevel, final int capacity) {
        if (capacity == this.builtCapacity && this.containerProvider != null) {
            return;
        }
        if (this.joinedNetwork) {
            // Already on the network at a different size. The node cannot be swapped in place, so
            // this is done by rebuilding the block entity on the next tick.
            this.requestRecreate(currentLevel);
            return;
        }
        this.node = new PatternProviderNetworkNode(this.node.getEnergyUsage(), capacity);
        this.containerProvider = this.buildContainerProvider();
        this.containerProvider.initialize(currentLevel, null);
        this.joinedNetwork = true;
        this.builtCapacity = capacity;
        // The new node has no patterns at all, so the next push must not be skipped.
        this.pushedPatternsVersion = -1;
    }

    /**
     * Hands the structure's patterns to the node.
     *
     * <p>Guarded by a version sum rather than done on every refresh. Turning an item into a
     * {@link Pattern} parses it, and a large structure holds hundreds -- doing that once a second to
     * usually discover that nothing had changed would be real work for no result.
     */
    private void pushPatternsIfChanged(final Level currentLevel, final StructurePatterns patterns) {
        final int version = patterns.patternsVersion();
        if (version == this.pushedPatternsVersion) {
            return;
        }
        // Bounded by what the node actually has, not by what the structure has. When capacity has
        // changed the node is still the old size until the rebuild lands a tick later, and writing
        // past it is an ArrayIndexOutOfBounds inside RS -- another server crash. Being briefly out
        // of date is fine; the rebuild forces a full re-push when it arrives.
        final int slots = Math.min(patterns.getContainerSize(), this.builtCapacity);
        if (slots < patterns.getContainerSize()) {
            return;
        }
        for (int slot = 0; slot < slots; slot++) {
            final ItemStack stack = patterns.getItem(slot);
            final Pattern pattern = stack.isEmpty()
                ? null
                : RefinedStorageApi.INSTANCE.getPattern(stack, currentLevel).orElse(null);
            this.node.setPattern(slot, pattern);
        }
        this.pushedPatternsVersion = version;
    }

    private void updateScreen(final Level currentLevel, final Result result) {
        final BlockState current = this.getBlockState();
        if (!current.hasProperty(ControllerBlock.STATE)) {
            return;
        }
        final ControllerState wanted;
        if (!result.formed()) {
            wanted = ControllerState.UNFORMED;
        } else {
            wanted = this.hasEnergy() ? ControllerState.ACTIVE : ControllerState.INACTIVE;
        }
        if (current.getValue(ControllerBlock.STATE) != wanted) {
            currentLevel.setBlock(this.worldPosition,
                current.setValue(ControllerBlock.STATE, wanted), Block.UPDATE_ALL);
        }
    }


    /**
     * Rebuilds this block entity from scratch, on the next tick, so the node can change size.
     *
     * <p><strong>A container cannot be removed while its block still stands.</strong>
     * {@code NetworkBuilderImpl.remove} walks the network from a neighbouring container and requires
     * the removed one to be <em>absent</em> from that rescan -- but the capability at our position
     * keeps answering as long as the block entity is there, so RS finds it, fails its own validation
     * and throws. That is a hard server crash, and it took three wrong theories to find: it is not
     * about initialisation order, and not about RS's deferred task queue.
     *
     * <p>Dropping the block entity makes the capability answer nothing, which is exactly the state
     * RS expects during a removal -- so {@code setRemoved} takes the container out cleanly and the
     * fresh block entity builds a node at the new size.
     *
     * <p>Nothing is lost, because nothing here is worth keeping: capacity, patterns and speed are
     * all derived from the world, and the patterns themselves live in the Pattern Storage blocks.
     * The decision to keep them there pays for itself here.
     *
     * <p>Deferred to the server's task queue rather than done inline, because this runs from inside
     * the block entity tick and removing a block entity while the level is iterating them is how you
     * get a concurrent modification instead of a working crafter.
     */
    private void requestRecreate(final Level currentLevel) {
        if (this.recreateRequested || currentLevel.getServer() == null) {
            return;
        }
        this.recreateRequested = true;
        final BlockPos pos = this.worldPosition;
        currentLevel.getServer().execute(() -> {
            if (currentLevel.getBlockEntity(pos) != this) {
                return;
            }
            currentLevel.removeBlockEntity(pos);
            final BlockState state = currentLevel.getBlockState(pos);
            if (state.getBlock() instanceof ControllerBlock controllerBlock) {
                final BlockEntity fresh = controllerBlock.newBlockEntity(pos, state);
                if (fresh != null) {
                    currentLevel.setBlockEntity(fresh);
                }
            }
        });
    }
    /**
     * Whether the structure is actually running.
     *
     * <p><strong>"Has a network" is not a test of anything.</strong> RS's {@code NetworkBuilderImpl}
     * creates a network for a lone container when there is nothing to merge with, so
     * {@code getNetwork() != null} is true the moment this block initialises, cabled or not. An
     * earlier version used it and the screen lit up for every Controller ever placed.
     *
     * <p>So this asks RS's own question, the one {@code calculateActive} asks of every RS machine:
     * is energy required at all, and if so does the network hold at least what this structure draws.
     * A one-node network of our own making stores nothing, so it fails that on its own.
     */
    private boolean hasEnergy() {
        if (!RefinedStorageApi.INSTANCE.isEnergyRequired()) {
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
        if (this.containerProvider != null && this.joinedNetwork && this.node.getNetwork() != null) {
            this.containerProvider.remove(this.level);
            this.joinedNetwork = false;
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        // Deliberately does NOT join the network here.
        //
        // The node's pattern capacity is fixed at construction, and at this point the structure has
        // not been read yet -- so joining now means joining at size zero, discovering the real size
        // a tick later, and rebuilding. Which rebuilds into another size-zero node, and so on: an
        // endless recreate loop that showed up as a structure that never powered on.
        //
        // The first refresh joins, once the size is known. An unformed structure therefore is not
        // on the network at all, which is also the more honest answer.
    }
}

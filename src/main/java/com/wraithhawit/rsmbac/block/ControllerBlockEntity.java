package com.wraithhawit.rsmbac.block;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import com.wraithhawit.rsmbac.Config;
import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.content.RsmcBlockEntities;
import com.wraithhawit.rsmbac.integration.FluidSubstitution;
import com.wraithhawit.rsmbac.menu.StructurePatterns;
import com.wraithhawit.rsmbac.structure.CraftingBudget;
import com.wraithhawit.rsmbac.structure.JoinGrace;
import com.wraithhawit.rsmbac.structure.PatternChanges;
import com.wraithhawit.rsmbac.structure.LevelBlockSource;
import com.wraithhawit.rsmbac.structure.RefreshSchedule;
import com.wraithhawit.rsmbac.structure.StructureChanges;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;
import com.wraithhawit.rsmbac.structure.StructurePower;
import com.wraithhawit.rsmbac.structure.StructureStepBehavior;

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
    /**
     * When to re-derive the structure.
     *
     * <p>Was a flat "every 20 ticks" until 0.1.9, which walked up to 4096 positions once a second
     * for a machine that was not doing anything -- measured at 0.19 ms/tick for one block. Now
     * change-driven and debounced, with a slow safety scan; see {@link RefreshSchedule} for why
     * the safety scan is not optional.
     */
    private final RefreshSchedule refresh = new RefreshSchedule();

    /**
     * The last speed handed to Refined Storage.
     *
     * <p>Kept because {@code PatternProviderNetworkNode.stepBehavior} is private with only a
     * setter, so there is otherwise no way to see what RS was actually told -- and that number is
     * the entire mod. If {@code setStepBehavior} were never called, or called with the wrong
     * value, every other test would still pass and the crafter would silently run at Refined
     * Storage's default speed. This is what {@code theStructureTellsRefinedStorageItsSpeed}
     * asserts on.
     */
    private StructureStepBehavior stepBehavior = StructureStepBehavior.IDLE;

    /**
     * How many patterns are handed to the network per refresh. See the comment where it is used.
     *
     * <p>Eight a second fills a storage block in under seven, which is faster than anyone fills one
     * by hand, and slow enough that the listener storm never lands in one tick.
     *
     * <p><strong>"A second" was untrue between 0.1.9 and 0.5.0.</strong> That release made the
     * refresh change-driven, and a pattern arriving is not a block change -- so the only thing that
     * ever ran this was the ten-second safety scan, making the real rate eight patterns per ten
     * seconds and a full storage block over a minute. {@link PatternChanges} is the counter that
     * makes the sentence above true again; without it a Pattern Port looks broken rather than slow.
     */
    private static final int PATTERN_PUSHES_PER_REFRESH = 8;

    /**
     * The pattern provider. Rebuilt when the structure's pattern capacity changes, because
     * {@link PatternProviderNetworkNode} fixes its slot count at construction.
     */
    private PatternProviderNetworkNode node = new PatternProviderNetworkNode(0L, 0);

    @Nullable
    private NetworkNodeContainerProvider containerProvider;

    /** What the current node was built for, so a rebuild happens only when it must. */
    private int builtCapacity;

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

    /** What Refined Storage was last told this structure's speed is. */
    public StructureStepBehavior stepBehavior() {
        return this.stepBehavior;
    }

    private void setStepBehavior(final StructureStepBehavior behavior) {
        this.stepBehavior = behavior;
        this.node.setStepBehavior(behavior);
        // The budget compares against what RS was last told, and syncNode has just told it
        // something directly. Without this a structure that grew or lost power would keep the old
        // comparison and skip the next update.
        this.toldSteps = behavior.stepsPerTick();
    }

    /** What Refined Storage was last told, which is the structure's rate minus any throttling. */
    private int toldSteps = -1;

    /** What the tick budget currently allows, for {@code /rsmbac info}. */
    public int currentAllowance() {
        return this.budget.currentAllowance(this.stepBehavior.stepsPerTick());
    }

    public PatternProviderNetworkNode node() {
        return this.node;
    }

    /**
     * Performs crafting, inside a slice of the tick.
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
        final long budgetNanos = Config.budgetNanos();
        // The STRUCTURE's rate, never the throttled one. this.stepBehavior is only ever written by
        // syncNode, so it stays the ceiling; reading back what we last told Refined Storage would
        // ratchet the allowance downwards and never let it recover.
        final int rate = this.stepBehavior.stepsPerTick();
        final int allowed = this.budget.allowedSteps(rate, budgetNanos);
        // Told to RS only when it changed. Cheap either way, but a write per tick to a field the
        // crafting engine reads is worth not doing for no reason.
        if (allowed != this.toldSteps) {
            this.node.setStepBehavior(
                new StructureStepBehavior(allowed, this.stepBehavior.active()));
            this.toldSteps = allowed;
        }
        final long started = System.nanoTime();
        this.node.doWork();
        // nanoTime rather than the game clock: this is an elapsed interval, and a monotonic source
        // cannot hand back a negative one if the wall clock moves under us.
        this.budget.record(System.nanoTime() - started, allowed, rate, budgetNanos);
    }

    /**
     * Keeps the structure's crafting inside a slice of the tick. See {@link CraftingBudget}.
     *
     * <p>Per structure rather than global on purpose: two structures each get their own slice, so a
     * second one does not silently halve the first. That is the wrong answer for a server with many
     * of them and the right one for the case this exists to fix, which is a single enormous crafter
     * flattening the tick on its own.
     */
    private final CraftingBudget budget = new CraftingBudget();

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
        if (!this.refresh.shouldScan(currentLevel.getGameTime(),
            StructureChanges.generation(), PatternChanges.generation())) {
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
            this.setStepBehavior(StructureStepBehavior.IDLE);
            this.node.setActive(false);
            // Only here, never on the formed path. A structure that is already formed joins below
            // at its real pattern capacity in one step; joining early at capacity zero would force
            // an immediate rebuild to resize, which delayed activation by a tick and was caught by
            // apoweredstructuregoesactive. See joinNetworkIfNeeded for why the unformed case has
            // to join at all.
            this.joinNetworkIfNeeded(currentLevel);
            return;
        }
        this.node.setEnergyUsage(StructurePower.energyUsage(result));
        final StructurePatterns patterns = StructurePatterns.of(currentLevel, result);
        this.ensureCapacity(currentLevel, patterns.getContainerSize());
        this.pushPatternsIfChanged(currentLevel, patterns);
        final boolean active = this.hasEnergy();
        this.node.setActive(active);
        this.setStepBehavior(new StructureStepBehavior(result.stepsPerTick(), active));
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
    /**
     * Joins the network even when the structure has not formed.
     *
     * <h2>The trap this closes</h2>
     *
     * <p>Until 0.1.12 the container was initialised in exactly one place -- {@link #ensureCapacity}
     * -- which {@link #syncNode} only reached <em>after</em> its "not formed" early exit. So an
     * unformed Controller never joined a network, and {@code node.getNetwork()} stayed null.
     *
     * <p>That is not harmless, because Refined Storage's security asks this block for permission.
     * {@code SecurityHelper.isAllowed} reads:
     *
     * <pre>{@code
     * Network network = node.getNetwork();
     * return network == null ? false : isAllowed(player, permission, network);
     * }</pre>
     *
     * <p><b>No network means denied</b>, unconditionally -- there is nothing to secure, no owner
     * and no team, and the answer is still no. RS then applies that answer in two places: a global
     * {@code BlockEvent.BreakEvent} handler, and {@code canPlaceNetworkNode}, which checks all six
     * neighbours of anything being placed.
     *
     * <p>The result was a deadlock. An unformed Controller could not be broken ("You are not
     * allowed to break the Crafter Controller"), and no network block could be placed next to it
     * -- so the structure could not be completed <em>or</em> removed without commands. Reported by
     * Wraith after the 0.1.11 CPU rename unformed an existing crafter.
     *
     * <p>Joining while unformed is also simply more correct. The Controller is the structure's
     * network face from the moment it is placed; whether the box around it is finished is a
     * question about crafting, not about connectivity. It joins with zero pattern capacity and an
     * IDLE step behaviour, so it contributes nothing until the box is real.
     */
    private void joinNetworkIfNeeded(final Level currentLevel) {
        if (this.joinedNetwork) {
            return;
        }
        // containerProvider(), NOT a fresh buildContainerProvider(). The capability accessor may
        // already have built one and handed it to Refined Storage, and replacing the field then
        // leaves RS holding a container from the old provider while removal offers the new one --
        // which is a hard crash on the way out: "The removed container should be present in the
        // removed entries, but isn't". The gametests caught exactly that. ShellBlockEntity has
        // always done it this way; this now matches.
        this.containerProvider().initialize(currentLevel, null);
        this.joinedNetwork = true;
        // The starting node is built with zero capacity, so nothing is claimed that is not true.
        this.builtCapacity = 0;
    }

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
        // The new node holds nothing, so everything has to be pushed into it again.
        StructurePatterns.of(currentLevel, this.worldPosition).markAllDirty();
    }

    /**
     * Hands the changed patterns to the node.
     *
     * <p><strong>Only the slots that changed.</strong> {@code setPattern} is not a store -- it tells
     * the autocrafting component to remove the old pattern and add the new one, which invalidates
     * Refined Storage's crafting indexes. Re-pushing every slot because one changed redoes that for
     * the whole structure, which is what a multi-second freeze per shift-click turned out to be.
     */
    private void pushPatternsIfChanged(final Level currentLevel, final StructurePatterns patterns) {
        if (!patterns.hasDirtySlots()) {
            return;
        }
        // The node is still the old size until a capacity rebuild lands a tick later, and writing
        // past it is an ArrayIndexOutOfBounds inside RS. Waiting costs nothing: the rebuild marks
        // every slot dirty again.
        if (patterns.getContainerSize() != this.builtCapacity) {
            return;
        }
        // A few at a time, never the whole backlog at once.
        //
        // setPattern is far more than a store: it calls remove and then add on the network's
        // autocrafting component, and add ends with
        // patternListeners.forEach(listener -> listener.onAdded(pattern)). Refined Storage keeps
        // four calculator listeners on that path, so one pattern means four notifications and
        // whatever recalculation each decides to do.
        //
        // Draining every dirty slot in one refresh therefore lands the whole cost of a shift-click
        // in a single tick, which is what a hard lock-up while inserting patterns turned out to be.
        // Spreading it means a bulk insert registers over a few seconds, which nobody notices,
        // instead of freezing the server once, which everybody does.
        final int[] budget = {PATTERN_PUSHES_PER_REFRESH};
        patterns.drainDirtySlots(slot -> {
            if (budget[0]-- <= 0) {
                // Out of budget: hand it back, so the next refresh picks it up.
                patterns.markDirty(slot);
                return;
            }
            final ItemStack stack = patterns.getItem(slot);
            final Pattern pattern = stack.isEmpty()
                ? null
                : RefinedStorageApi.INSTANCE.getPattern(stack, currentLevel).orElse(null);
            this.node.setPattern(slot, pattern);
        });
        // After the drain, because a fluid-substitution pattern needs its helper patterns rebuilt
        // as a set: they are packed after the container and deduplicated across slots, so there is
        // no per-slot version of this. Only reached when something was actually dirty, and a no-op
        // unless Refined Fluid Substitution is installed.
        FluidSubstitution.refresh(currentLevel, this.node, patterns);
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
        final ControllerState showing = current.getValue(ControllerBlock.STATE);
        if (this.stillJoining(currentLevel, showing, wanted)) {
            return;
        }
        if (showing != wanted) {
            // Logged on every transition, because this is the one moment the server can prove what
            // it decided and when. A stuck screen was reported once before and closed in 0.0.8 on a
            // gametest that only ever exercised the server path -- there is no client in a gametest,
            // so the half that can actually be stale was never observed, and the report came back.
            //
            // Reading it next time: a line ending "-> active" while the block still looks unpowered
            // in game means the value was right and the update did not reach the client. NO line at
            // all, across a whole session, means this method is not running -- and since the node's
            // active flag is set from the same hasEnergy() a few statements earlier, that would also
            // explain how a structure keeps crafting while its screen is frozen.
            RSMBAC.LOGGER.info("[rsmbac] controller screen {} -> {} at {} (tick {}, node active={},"
                    + " energy {}/{} FE)",
                showing.getSerializedName(), wanted.getSerializedName(), this.worldPosition,
                currentLevel.getGameTime(), this.node.isActive(),
                this.energyStored(), this.node.getEnergyUsage());
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
        final long stored = this.energyStored();
        // A missing network reads as -1 rather than 0, and is rejected here rather than being
        // allowed to satisfy a structure whose draw happens to be zero.
        return stored >= 0L && stored >= this.node.getEnergyUsage();
    }

    /**
     * Whether to leave the screen alone because the node has not finished joining a network yet.
     *
     * <p>The decision itself lives in {@link JoinGrace}, which carries the reasoning and is pinned
     * by {@code HeadlessJoinGraceCheck} — in particular that the grace <em>expires</em>, since a
     * screen that will not go dark would be a worse bug than the one this fixes.
     */
    private boolean stillJoining(final Level currentLevel, final ControllerState showing,
                                 final ControllerState wanted) {
        return this.joinGrace.suppressDarkening(
            currentLevel.getGameTime(),
            showing == ControllerState.ACTIVE && wanted == ControllerState.INACTIVE,
            this.energyStored() >= 0L);
    }

    /** Suppresses the load-time screen flicker; see {@link JoinGrace}. */
    private final JoinGrace joinGrace = new JoinGrace();

    /**
     * What the network is holding, or {@code -1} when there is no network to ask.
     *
     * <p>Extracted so {@link #hasEnergy} and the diagnostics read the <em>same</em> number. A
     * diagnostic that computes the answer a second way tells you about itself rather than about
     * the thing it is diagnosing, which is the rule the rest of the reporting here already follows.
     */
    private long energyStored() {
        final Network network = this.node.getNetwork();
        return network == null
            ? -1L
            : network.getComponent(EnergyNetworkComponent.class).getStored();
    }

    /**
     * What the node actually believes right now, for {@code /rsmbac info} to print beside the
     * screen.
     *
     * <p>These are the two halves that can disagree. Refined Storage's {@code doWork} returns
     * immediately unless {@code isActive()}, so a structure that is crafting <em>proves</em> the
     * node is active -- and if the screen says otherwise at the same moment, the screen is the
     * wrong half. Without printing both, telling them apart takes a round trip per guess.
     */
    public record Diagnostics(boolean nodeActive, boolean stepBehaviorActive, int stepsPerTick,
                              boolean energyRequired, long energyStored, long energyUsage) {
    }

    /** A snapshot of {@link Diagnostics}: reads state, changes none. */
    public Diagnostics diagnostics() {
        return new Diagnostics(this.node.isActive(), this.stepBehavior.active(),
            this.stepBehavior.stepsPerTick(), RefinedStorageApi.INSTANCE.isEnergyRequired(),
            this.energyStored(), this.node.getEnergyUsage());
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

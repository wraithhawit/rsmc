package com.wraithhawit.rsmbac.test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.autocrafting.CraftingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.PatternState;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.StonecutterPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.structure.StructureStepBehavior;
import com.wraithhawit.rsmbac.Config;
import com.wraithhawit.rsmbac.PatternImport;
import com.wraithhawit.rsmbac.RSMBAC;
import com.wraithhawit.rsmbac.block.ControllerBlock;
import com.wraithhawit.rsmbac.block.ControllerState;
import com.wraithhawit.rsmbac.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmbac.content.RsmcBlocks;
import com.wraithhawit.rsmbac.menu.StructurePatterns;
import com.wraithhawit.rsmbac.structure.CpuTier;
import com.wraithhawit.rsmbac.structure.StructurePower;
import com.wraithhawit.rsmbac.structure.LevelBlockSource;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The structure rules against a real level: {@code ./gradlew runGameTestServer}.
 *
 * <p>{@link HeadlessShapeCheck} already covers the geometry far more thoroughly and without
 * launching anything, so these deliberately do not re-test it. What only a real level can prove is
 * the half that the headless suite stubs out:
 *
 * <ul>
 *   <li>the blocks are registered and carry the role they claim
 *   <li>{@link LevelBlockSource} reads them back correctly, including that a non-rsmbac block reads
 *       as absent
 *   <li>the two halves agree -- a structure built out of the real blocks forms
 * </ul>
 *
 * <p>An 8x8x8 template, because the smallest legal structure is 3x3x4 and a 1x1x1 has nowhere to
 * build one.
 */
@GameTestHolder(RSMBAC.MODID)
@PrefixGameTestTemplate(false)
public final class StructureGameTests {
    /** The smallest legal structure, built from the real blocks, forms. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void smallestStructureForms(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.FOUR_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final Result result = find(helper);
        if (!result.formed()) {
            helper.fail("expected a structure, got " + result.failure());
            return;
        }
        if (result.stepsPerTick() != 4) {
            helper.fail("expected 4 steps/tick from one 4x CPU, got " + result.stepsPerTick());
            return;
        }
        if (result.cpus() != 1 || result.patternStorages() != 1) {
            helper.fail("expected 1 CPU and 1 pattern storage, got " + result.cpus()
                + " and " + result.patternStorages());
            return;
        }
        helper.succeed();
    }

    /** A missing wall block is found, and reported at the position that is missing. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aHoleIsReportedWhereItIs(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        // A wall panel: one coordinate at an extreme.
        helper.setBlock(new BlockPos(1, 0, 1), net.minecraft.world.level.block.Blocks.AIR);

        final Result result = find(helper);
        if (result.formed()) {
            helper.fail("a structure with a hole in it formed");
            return;
        }
        if (result.failure() != Failure.NOT_SOLID) {
            helper.fail("expected NOT_SOLID, got " + result.failure());
            return;
        }
        helper.succeed();
    }

    /**
     * A foreign block in a structure slot is not quietly treated as one of ours.
     *
     * <p>Worth its own test because {@link LevelBlockSource} decides this with an
     * {@code instanceof StructureBlock}, and that is the single line standing between "the shape
     * code sees the world correctly" and "any block completes your multiblock".
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aForeignBlockDoesNotCount(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 1), net.minecraft.world.level.block.Blocks.IRON_BLOCK);

        final Result result = find(helper);
        if (result.formed()) {
            helper.fail("an iron block completed the structure");
            return;
        }
        helper.succeed();
    }

    /**
     * Every block drops itself when mined with a pickaxe.
     *
     * <p>Guarding a failure that is completely silent. All seven blocks are
     * {@code requiresCorrectToolForDrops}, and the only thing that makes any tool the correct one
     * is membership of a {@code minecraft:mineable/*} tag. Miss the tag and the block drops nothing
     * with any tool, forever -- while the loot table stays perfectly valid, nothing errors, and no
     * check that merely looks for missing files notices. That is exactly what happened here.
     *
     * <p><strong>The tool check has to be {@code isCorrectToolForDrops}, not {@code getDrops}.</strong>
     * The first version of this test asked {@link Block#getDrops} for the drops while passing a
     * pickaxe, and it passed happily with the tag file emptied -- because {@code getDrops} only ever
     * runs the loot table, and the loot table has nothing to do with tool correctness. That gate
     * lives in {@code ServerPlayerGameMode}, which asks whether the tool is correct and only then
     * calls the drop path at all. A test that never fails when the bug is present is worse than no
     * test, so both halves are asserted separately here: the tool is correct for the block, and the
     * loot table names the block.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void everyBlockDropsItselfWithAPickaxe(final GameTestHelper helper) {
        final ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (final DeferredBlock<? extends Block> deferred : RsmcBlocks.all()) {
            final Block block = deferred.get();
            final BlockPos pos = new BlockPos(0, 0, 0);
            helper.setBlock(pos, block);
            final BlockPos absolute = helper.absolutePos(pos);
            // The real gate. False here means no tool is correct, which means no drops, whatever
            // the loot table says.
            if (!pickaxe.isCorrectToolForDrops(helper.getLevel().getBlockState(absolute))) {
                helper.fail(deferred.getId() + " is not mineable with an iron pickaxe -- it is"
                    + " missing from minecraft:mineable/pickaxe, so it will drop nothing");
                return;
            }
            final List<ItemStack> drops = Block.getDrops(
                helper.getLevel().getBlockState(absolute),
                helper.getLevel(),
                absolute,
                helper.getLevel().getBlockEntity(absolute),
                null,
                pickaxe);
            if (drops.isEmpty()) {
                helper.fail(deferred.getId() + " dropped nothing when mined with an iron pickaxe"
                    + " -- it is probably missing from minecraft:mineable/pickaxe");
                return;
            }
            if (!drops.getFirst().is(block.asItem())) {
                helper.fail(deferred.getId() + " dropped " + drops.getFirst() + " instead of itself");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Refined Storage can see every block a cable might touch.
     *
     * <p>The payoff test for the connectivity work, and it asks the question the way RS asks it:
     * resolve the network node container provider capability at the position. That is literally
     * what {@code PlatformImpl.getContainerProviderSafely} does when a cable probes a neighbour, so
     * a null here means a cable touching the structure joins nothing -- while every block still
     * places, renders and breaks perfectly, and nothing logs a word.
     *
 *
     * <p>All three shell blocks, because a cable must be able to touch any face of the box. The
     * Controller hosts the real node; Frame and Casing are relays that exist only so the probe finds
     * something. If the shell registration were ever dropped as an optimisation, this is what would
     * catch it -- and the symptom in game would be "cabling only works at the Controller", which is
     * the thing it was dropped to avoid.
     */
    /**
     * An unformed Controller must still be on a network, or it cannot be broken.
     *
     * <h2>The bug this reproduces</h2>
     *
     * <p>Reported from in game after 0.1.11: <em>"You are not allowed to break the Crafter
     * Controller"</em>, Refined Storage's own security toast, on a lone Controller.
     *
     * <p>Nothing about rsmbac was denying it. RS runs a global {@code BlockEvent.BreakEvent} handler
     * over any position exposing a network-node capability, and {@code SecurityHelper.isAllowed}
     * reads {@code network == null ? false : ...} — <b>no network means denied</b>, with no owner
     * and nothing to protect. The Controller only joined a network from {@code ensureCapacity},
     * which {@code syncNode} reached only after its "not formed" early exit, so an unformed one
     * never joined and became permanently unbreakable. {@code canPlaceNetworkNode} checks all six
     * neighbours the same way, so nothing could be placed beside it either — the structure could
     * be neither finished nor removed.
     *
     * <p>Asserting on the network rather than on the toast because the network is the condition RS
     * actually tests; a test that drove a fake player through the break would be testing RS.
     *
     * <p>The Frame and Casing are checked too. They initialise unconditionally in
     * {@code clearRemoved} and always passed — which is exactly why the Controller being different
     * went unnoticed, and why they belong here as the control.
     */
    /**
     * The structure tells Refined Storage its speed, and the number is the sum of its CPU tiers.
     *
     * <h2>Why this is the test issue #2 needed</h2>
     *
     * <p>{@code StepBehavior} is Refined Storage's entire crafting throughput model — a stock
     * autocrafter is 0.1 to 2.5 steps/tick and 2.5 is the ceiling — and rsmbac exists to answer
     * {@code getSteps} with a bigger number. That single call is the mod.
     *
     * <p>Nothing proved it. {@code smallestStructureForms} asserts what {@code MultiblockShape}
     * computes, which is arithmetic over blocks; {@code aPoweredStructureGoesActive} asserts the
     * screen turns blue. Both pass whether or not {@code setStepBehavior} is ever called. If it
     * were dropped, the crafter would form, light up, accept patterns, craft — and run at Refined
     * Storage's default speed, which is the one symptom nobody would think to look for.
     *
     * <p>Three CPU tiers rather than one, because a sum is the claim: 1 + 4 + 16 is 21, and it is
     * distinct from every plausible mistake — not the count (3), not the maximum (16), not the
     * first (1).
     *
     * <h2>What this does NOT prove, stated because the first version of it pretended otherwise</h2>
     *
     * <p>It asserts on the behaviour the Controller <em>recorded</em>, not on what
     * {@code PatternProviderNetworkNode} received. Deleting the {@code node.setStepBehavior(...)}
     * call entirely leaves this test green — verified, not assumed.
     *
     * <p>That gap is not fixable from here. RS keeps {@code stepBehavior} private with no getter,
     * and {@code setAccessible} is refused across its module, so the value it holds cannot be read
     * by any test in this mod. What covers the handoff is the structure demonstrably crafting at
     * speed in game; what covers everything up to it is this.
     *
     * <p>The name says "is the sum of its CPU tiers" rather than "tells Refined Storage" for
     * exactly that reason.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void theStructureSpeedIsTheSumOfItsCpuTiers(final GameTestHelper helper) {
        // A 2x1x2 interior: exactly four slots for three CPUs and one Pattern Storage.
        //
        // The interior must be FILLED -- every interior position holds a CPU or a Pattern Storage,
        // and a gap is NOT_SOLID. Two runs of this test read "0 steps/tick" before that landed,
        // both times because the box had not formed at all, which is correctly IDLE. The failure
        // message below now says so rather than leaving it to be rediscovered.
        buildShellSized(helper, 1, 4, 2, 3);
        helper.setBlock(new BlockPos(2, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(2, 1, 2), RsmcBlocks.CPUS.get(CpuTier.FOUR_X).get());
        helper.setBlock(new BlockPos(3, 1, 1), RsmcBlocks.CPUS.get(CpuTier.SIXTEEN_X).get());
        helper.setBlock(new BlockPos(3, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        final BlockPos cable = controller.relative(Direction.WEST);
        helper.setBlock(cable, rsBlock("cable"));
        helper.setBlock(cable.relative(Direction.WEST), rsBlock("creative_controller"));

        helper.runAfterDelay(60L, () -> {
            if (!(helper.getBlockEntity(controller) instanceof ControllerBlockEntity blockEntity)) {
                helper.fail("no ControllerBlockEntity at " + controller);
                return;
            }
            final StructureStepBehavior behavior = blockEntity.stepBehavior();
            if (behavior.stepsPerTick() != 21) {
                final Result shape = find(helper);
                helper.fail("told Refined Storage " + behavior.stepsPerTick()
                    + " steps/tick, expected 21 from a 1x + 4x + 16x interior"
                    + (shape.formed() ? "" : "  -- and the structure is not formed: "
                        + shape.failure() + ", so the test's geometry is wrong, not the mod"));
                return;
            }
            if (!behavior.active()) {
                helper.fail("powered and formed, but the step behaviour is inactive");
                return;
            }
            // The two methods RS actually calls. A behaviour that holds the right number and
            // answers canStep(false) still crafts nothing.
            if (!behavior.canStep(null) || behavior.getSteps(null) != 21) {
                helper.fail("canStep/getSteps disagree with the recorded behaviour: "
                    + behavior.canStep(null) + " / " + behavior.getSteps(null));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Losing power stops the structure, rather than letting it craft on at the same rate.
     *
     * <p>The other half of the claim above: {@code getSteps} must go to zero, not merely have
     * {@code active} flipped somewhere the task engine never reads. Same caveat — this proves the
     * behaviour object is right, not that RS was handed it.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void anUnpoweredStructureReportsZeroSteps(final GameTestHelper helper) {
        buildShell(helper, 1);
        helper.setBlock(new BlockPos(2, 1, 1), RsmcBlocks.CPUS.get(CpuTier.SIXTY_FOUR_X).get());
        helper.setBlock(new BlockPos(2, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        // Deliberately no cable: formed, but attached to nothing that can power it.
        helper.runAfterDelay(60L, () -> {
            if (!(helper.getBlockEntity(controller) instanceof ControllerBlockEntity blockEntity)) {
                helper.fail("no ControllerBlockEntity at " + controller);
                return;
            }
            final StructureStepBehavior behavior = blockEntity.stepBehavior();
            if (behavior.getSteps(null) != 0 || behavior.canStep(null)) {
                helper.fail("unpowered, but told Refined Storage it can step "
                    + behavior.getSteps(null) + " times");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void anUnformedControllerIsStillBreakable(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(0, 0, 0);
        helper.setBlock(controller, RsmcBlocks.CONTROLLER.get());
        helper.setBlock(new BlockPos(2, 0, 0), RsmcBlocks.FRAME.get());
        helper.setBlock(new BlockPos(4, 0, 0), RsmcBlocks.CASING.get());

        // Long enough for the refresh to run and for RS to drain its queued initialisation, which
        // happens on the server tick rather than immediately.
        helper.runAfterDelay(20, () -> {
            for (final BlockPos pos : List.of(controller, new BlockPos(2, 0, 0),
                new BlockPos(4, 0, 0))) {
                final NetworkNodeContainerProvider provider = helper.getLevel().getCapability(
                    RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                    helper.absolutePos(pos),
                    null);
                if (provider == null) {
                    helper.fail("no container provider at " + pos);
                    return;
                }
                for (final InWorldNetworkNodeContainer container : provider.getContainers()) {
                    if (container.getNode().getNetwork() == null) {
                        helper.fail(helper.getBlockState(pos).getBlock()
                            + " has no network while unformed, so Refined Storage denies every"
                            + " break and every placement beside it");
                        return;
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void refinedStorageSeesTheShell(final GameTestHelper helper) {
        for (final Block block : List.of(RsmcBlocks.CONTROLLER.get(), RsmcBlocks.FRAME.get(),
            RsmcBlocks.CASING.get())) {
            final BlockPos pos = new BlockPos(0, 0, 0);
            helper.setBlock(pos, block);
            final NetworkNodeContainerProvider provider = helper.getLevel().getCapability(
                RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                helper.absolutePos(pos),
                null);
            if (provider == null) {
                helper.fail(block + " exposes no network node container -- a cable touching it"
                    + " would join nothing");
                return;
            }
            if (provider.getContainers().size() != 1) {
                helper.fail(block + " exposed " + provider.getContainers().size()
                    + " containers, expected exactly 1");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Breaking blocks out of a finished structure must turn the screen off.
     *
     * <p>Written to reproduce a report from in game: breaking the cable correctly darkened the
     * screen, but breaking blocks out of the crafter itself left it lit. The two go through the
     * same once-a-second refresh and differ only in which branch of {@code computeState} answers,
     * so this drives the whole path -- build, settle, break, wait -- rather than asserting on
     * {@code MultiblockShape.find} alone, which the headless suite already covers and which passes.
     *
     * <p>A long timeout because the refresh is a poll: up to a second to notice, and the test has
     * to outlast that or it would be testing its own patience.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void breakingBlocksTurnsTheScreenOff(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        // Let the poll run at least once so the screen has caught up with the finished structure.
        helper.runAfterDelay(45L, () -> {
            final ControllerState formed = stateAt(helper, controller);
            // INACTIVE exactly, not merely "not UNFORMED". There is no RS controller in this test
            // level, so the structure is formed and unpowered -- and an earlier version read
            // ACTIVE here, because it asked getNetwork() != null, which RS answers yes to for a
            // lone node it made a network for. The looser assertion passed that bug happily.
            if (formed != ControllerState.INACTIVE) {
                helper.fail("a complete but unpowered structure reads " + formed
                    + ", expected INACTIVE");
                return;
            }
            // Break a chunk of it, one of every type at once -- matching the report, which was
            // not a single tidy block but "a good chunk, all 4 types, even at the same time".
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);   // CPU
            helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);   // pattern storage
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);   // casing (wall)
            helper.setBlock(new BlockPos(0, 0, 0), Blocks.AIR);   // frame (corner)
            helper.runAfterDelay(45L, () -> {
                final ControllerState after = stateAt(helper, controller);
                if (after != ControllerState.UNFORMED) {
                    helper.fail("broke a CPU, a pattern storage, a casing and a frame, and the"
                        + " screen still reads " + after);
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * Lowering {@code maxStructureEdge} unforms a structure that no longer fits, promptly.
     *
     * <p>A 3x3x5 forms under the default limit and must read UNFORMED once the limit is 4. The
     * deadline is what makes this a test of the config wiring rather than of patience: twenty ticks
     * is a tenth of the safety scan, so only the change bump in {@code Config} can explain the
     * screen turning off in time. Remove the bump and this fails; point the Controller's
     * {@code find} back at {@code MAX_EDGE} and it fails.
     *
     * <p>Its own batch because the limit is global: run beside the other tests, lowering it would
     * unform their structures too.
     */
    @GameTest(template = "empty8", timeoutTicks = 200, batch = "maxStructureEdge")
    public static void loweringTheEdgeLimitUnformsATooLongStructure(final GameTestHelper helper) {
        final int original = Config.maxStructureEdge;
        buildShellSized(helper, 2, 2, 4);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        helper.runAfterDelay(45L, () -> {
            final ControllerState formed = stateAt(helper, controller);
            if (formed != ControllerState.INACTIVE) {
                helper.fail("a 3x3x5 under the default limit reads " + formed
                    + ", expected INACTIVE");
                return;
            }
            Config.setMaxStructureEdge(4);
            helper.runAfterDelay(20L, () -> {
                final ControllerState after = stateAt(helper, controller);
                Config.setMaxStructureEdge(original);
                if (after != ControllerState.UNFORMED) {
                    helper.fail("lowered maxStructureEdge to 4 and a 3x3x5 still reads " + after
                        + " twenty ticks later");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @Nullable
    private static BlockPos controllerPos(final GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 3; z++) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(RsmcBlocks.CONTROLLER.get())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static ControllerState stateAt(final GameTestHelper helper, final BlockPos pos) {
        return helper.getBlockState(pos).getValue(ControllerBlock.STATE);
    }

    /**
     * A structure cabled to a powered Refined Storage network goes light blue.
     *
     * <p>The one branch nothing else covers, and the one everything else will hang off: the pattern
     * provider only runs when the structure reads as powered, so if this is wrong the crafter is
     * silently dead and every test above still passes.
     *
     * <p>It is also the branch the previous bug hid in. When "powered" was `getNetwork() != null`,
     * every Controller ever placed satisfied it -- so ACTIVE was reachable for the wrong reason and
     * a test asserting only "goes blue eventually" would have agreed with the bug. This builds a
     * real network with an RS Creative Controller and a cable, which is the only way to tell a
     * structure that is genuinely powered from one that merely exists.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void aPoweredStructureGoesActive(final GameTestHelper helper) {
        // Offset by one so the Controller's outward face has a free column for the cable.
        buildShell(helper, 1);
        helper.setBlock(new BlockPos(2, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(2, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        // Straight out of the Controller's exposed face, then RS's own infinite power source.
        final BlockPos cable = controller.relative(Direction.WEST);
        helper.setBlock(cable, rsBlock("cable"));
        helper.setBlock(cable.relative(Direction.WEST), rsBlock("creative_controller"));

        helper.runAfterDelay(60L, () -> {
            final ControllerState state = stateAt(helper, controller);
            if (state != ControllerState.ACTIVE) {
                helper.fail("cabled to a creative controller and the structure reads " + state
                    + ", expected ACTIVE");
                return;
            }
            helper.succeed();
        });
    }

    /** Looks up a Refined Storage block by name, so the test says what it means. */
    private static Block rsBlock(final String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", name));
    }

    /**
     * A Pattern Storage block keeps its patterns, and hands them back when broken.
     *
     * <p>These are the two ways an inventory loses things quietly. Neither shows up as an error:
     * a pattern that failed to save is simply gone next session, and one that failed to drop is
     * gone the moment somebody rearranges their build. Both are exactly the data loss that putting
     * patterns in the block rather than on a controller was meant to prevent, so both get a test.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void patternsSurviveAndDrop(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, RsmcBlocks.PATTERN_STORAGE.get());
        if (!(helper.getBlockEntity(pos) instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no Pattern Storage block entity");
            return;
        }
        final ItemStack pattern = new ItemStack(rsItem("pattern"));
        storage.patterns().setItem(0, pattern.copy());

        // Round-trip through NBT the way a chunk save and reload does.
        final CompoundTag tag = storage.saveWithoutMetadata(helper.getLevel().registryAccess());
        final PatternStorageBlockEntity reloaded = new PatternStorageBlockEntity(
            helper.absolutePos(pos), helper.getBlockState(pos));
        reloaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        if (reloaded.patterns().getItem(0).isEmpty()) {
            helper.fail("the pattern did not survive a save and reload");
            return;
        }

        if (storage.getDrops().isEmpty()) {
            helper.fail("breaking a Pattern Storage would drop none of its patterns");
            return;
        }
        helper.succeed();
    }

    /** Looks up a Refined Storage item by name, so the test says what it means. */
    private static net.minecraft.world.item.Item rsItem(final String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", name));
    }

    /**
     * The structure's pattern view spans every Pattern Storage block, in a stable order.
     *
     * <p>The arithmetic that maps a screen slot onto "which block, which slot inside it" is the
     * part most likely to be quietly wrong: an off-by-one lands a pattern in the wrong block, which
     * looks completely normal until someone breaks that block and the wrong patterns fall out.
     *
     * <p>Order matters as much as the count. Slots are sorted by position rather than by the order
     * the scan found them, because a player's patterns must not move around in the screen because a
     * chunk reloaded -- and because "which slot is slot 79" has to mean the same thing on the server
     * and the client, which agree on nothing except the world.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void theViewSpansEveryPatternStorage(final GameTestHelper helper) {
        // 3x3x6 gives an interior of 1x1x4: room for two storages with CPUs between them.
        buildShellSized(helper, 2, 2, 5);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 4), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final int perStorage = StructurePower.PATTERNS_PER_STORAGE;
        final StructurePatterns view =
            StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        if (view.storageCount() != 2) {
            helper.fail("expected 2 Pattern Storage blocks in the view, got " + view.storageCount());
            return;
        }
        if (view.getContainerSize() != perStorage * 2) {
            helper.fail("expected " + (perStorage * 2) + " slots, got " + view.getContainerSize());
            return;
        }

        // The last slot of the first block and the first slot of the second: the two either side of
        // the boundary, which is where the arithmetic goes wrong if it is going to.
        final ItemStack pattern = new ItemStack(rsItem("pattern"));
        view.setItem(perStorage - 1, pattern.copy());
        view.setItem(perStorage, pattern.copy());

        final BlockPos first = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos second = helper.absolutePos(new BlockPos(1, 1, 3));
        if (!(helper.getLevel().getBlockEntity(first) instanceof PatternStorageBlockEntity a)
            || !(helper.getLevel().getBlockEntity(second) instanceof PatternStorageBlockEntity b)) {
            helper.fail("lost a Pattern Storage block entity");
            return;
        }
        if (a.patterns().getItem(perStorage - 1).isEmpty()) {
            helper.fail("slot " + (perStorage - 1) + " did not land in the first storage block");
            return;
        }
        if (b.patterns().getItem(0).isEmpty()) {
            helper.fail("slot " + perStorage + " did not land in the second storage block");
            return;
        }
        helper.succeed();
    }

    /**
     * A backlog far larger than the old budget drains in a single refresh, not over minutes.
     *
     * <p>The companion to {@code aRepushCostsOnePushPerPatternNotPerSlot}: that one proves empty
     * slots no longer cost anything, this one proves the <em>rate</em> is no longer eight a second.
     * 100 patterns is deliberately more than twelve times the old {@code PATTERN_PUSHES_PER_REFRESH}
     * of 8, so under the old code this could not pass however long it waited -- one refresh moved
     * eight, and the next refresh was a second away.
     *
     * <p>What is asserted is that nothing is left dirty. A dirty slot is one the network has not
     * been told about, so "no dirty slots" is exactly "every pattern is craftable".
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void awholeBacklogDrainsInOneRefresh(final GameTestHelper helper) {
        buildShellSized(helper, 2, 2, 5);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 4), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        final BlockPos cable = controller.relative(Direction.WEST);
        helper.setBlock(cable, rsBlock("cable"));
        helper.setBlock(cable.relative(Direction.WEST), rsBlock("creative_controller"));

        final StructurePatterns view =
            StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        final ItemStack pattern = new ItemStack(rsItem("pattern"));
        final int count = 100;
        for (int i = 0; i < count; i++) {
            view.setItem(i, pattern.copy());
        }

        // Long enough for the structure to form, join and settle. Under the old rate 100 patterns
        // needed about thirteen seconds of refreshes; this deadline is under three.
        helper.runAfterDelay(50L, () -> {
            final StructurePatterns after =
                StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
            if (after.hasDirtySlots()) {
                helper.fail("patterns are still waiting to reach the network after 50 ticks;"
                    + " the drain is rate-limited again");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A re-push costs one push per <em>pattern</em>, never one per slot.
     *
     * <p>This is the seven-hour bug. {@code markAllDirty} used to mark every slot whether or not it
     * held anything, and the Controller spends its budget of eight per refresh on empty slots
     * exactly as it does on real ones -- pushing a null into a node slot that is already null. Time
     * to become craftable therefore scaled with the size of the box instead of the number of
     * recipes, and a player with a few thousand patterns in a large structure waited hours.
     *
     * <p>The differential is the point: under the old implementation this reports the full capacity
     * rather than the two patterns actually present, so the test fails loudly rather than agreeing
     * with whatever the code happens to do.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aRepushCostsOnePushPerPatternNotPerSlot(final GameTestHelper helper) {
        buildShellSized(helper, 2, 2, 5);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 4), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final StructurePatterns view =
            StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        final int capacity = view.getContainerSize();
        final ItemStack pattern = new ItemStack(rsItem("pattern"));
        // One in each block, so the count cannot be right by only ever looking at the first.
        view.setItem(0, pattern.copy());
        view.setItem(StructurePower.PATTERNS_PER_STORAGE, pattern.copy());

        // Forget what the two inserts dirtied, so what follows measures the re-push alone.
        view.drainDirtySlots(slot -> { });

        // Exactly what a node rebuild and a world load ask for.
        view.markAllDirty();
        final int[] pushes = {0};
        view.drainDirtySlots(slot -> pushes[0]++);

        if (pushes[0] != 2) {
            helper.fail("re-pushing 2 patterns held in " + capacity + " slots cost " + pushes[0]
                + " pushes; empty slots are spending the budget again");
            return;
        }
        helper.succeed();
    }

    /**
     * Only patterns can go into a pattern slot.
     *
     * <p>Reported from in game: shift-clicking a non-pattern moved it into the crafter. The filter
     * is Refined Storage's own -- {@code PatternInventory} tests every stack with
     * {@code PatternProviderItem.isValid} -- so this asks the container the same question the
     * transfer path does, rather than trusting that delegation works.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void onlyPatternsFitInPatternSlots(final GameTestHelper helper) {
        buildShellSized(helper, 2, 2, 5);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 4), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final StructurePatterns view =
            StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        if (view.canPlaceItem(0, new ItemStack(Items.COBBLESTONE))) {
            helper.fail("a cobblestone was accepted into a pattern slot");
            return;
        }
        // And the slot in the second storage block, since that goes through the index arithmetic.
        if (view.canPlaceItem(StructurePower.PATTERNS_PER_STORAGE, new ItemStack(Items.COBBLESTONE))) {
            helper.fail("a cobblestone was accepted into the second storage block");
            return;
        }
        // A BLANK pattern is refused too, and that is correct: RS's filter is
        // PatternProviderItem.isValid, which wants an encoded pattern, not merely the right item.
        // Worth asserting rather than assuming -- it is the difference between "only patterns fit"
        // and "only patterns that actually make something fit".
        if (view.canPlaceItem(0, new ItemStack(rsItem("pattern")))) {
            helper.fail("an unencoded pattern was accepted");
            return;
        }
        helper.succeed();
    }

    /**
     * The import takes crafting patterns and leaves processing patterns exactly where they are.
     *
     * <p>Two things are under test and both would fail silently.
     *
     * <p><b>The reflection.</b> Neither RS's {@code AutocrafterBlockEntity} nor Cable Tiers' tiered
     * one exposes its {@code PatternInventory}, so the import finds the field by type -- and under
     * NeoForge every mod is a named <em>module</em>, where {@code setAccessible} on another module's
     * private field is exactly the kind of thing that compiles and then throws. RS's Autocrafter
     * stands in for Cable Tiers' here: both hold the same class, and only one is in a dev run.
     *
     * <p><b>The filter.</b> A processing pattern needs a sink to push its ingredients into. An
     * autocrafter is one; the multiblock is not. Moving one here would leave it advertised as
     * craftable and stalling forever -- so it stays put, still working, in the crafter it came from.
     * Crafting, stonecutter and smithing patterns are all {@code PatternLayout.internal} and all
     * move; only processing is {@code external}.
     * Asked before running the command for real: "I do not wanna run the command and have to figure
     * out where I stole all those processing patterns from."
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void theImportTakesCraftingPatternsAndLeavesProcessingOnes(
            final GameTestHelper helper) {
        buildShellSized(helper, 2, 2, 5);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 3), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 1, 4), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());

        final BlockPos controller = controllerPos(helper);
        if (controller == null) {
            helper.fail("the test shell did not place a Controller");
            return;
        }
        final BlockPos cable = controller.relative(Direction.WEST);
        helper.setBlock(cable, rsBlock("cable"));
        helper.setBlock(cable.relative(Direction.WEST), rsBlock("creative_controller"));
        final BlockPos autocrafter = cable.above();
        helper.setBlock(autocrafter, rsBlock("autocrafter"));

        helper.runAfterDelay(40L, () -> {
            final BlockEntity source =
                helper.getLevel().getBlockEntity(helper.absolutePos(autocrafter));
            if (source == null) {
                helper.fail("no Autocrafter block entity");
                return;
            }
            final Container inventory = reachPatterns(source);
            if (inventory == null) {
                helper.fail("could not reach the Autocrafter's PatternInventory by reflection --"
                    + " NeoForge's module system is blocking it, and the import cannot work");
                return;
            }
            // Interleaved, so the filter cannot pass by stopping at the first one it dislikes.
            inventory.setItem(0, craftingPattern());
            inventory.setItem(1, processingPattern());
            inventory.setItem(2, craftingPattern());
            inventory.setItem(3, processingPattern());
            // Not a crafting pattern, and still ours to run -- see stonecutterPattern().
            inventory.setItem(4, stonecutterPattern());

            final Result shape = find(helper);
            final PatternImport.Report report = PatternImport.run(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), shape, false);
            if (report.failed()) {
                helper.fail("import failed: " + report.failure());
                return;
            }
            if (report.sources() != 1) {
                helper.fail("expected to find 1 autocrafter, found " + report.sources()
                    + " -- the reflection is not reaching the pattern inventory");
                return;
            }
            if (report.moved() != 3) {
                helper.fail("expected 3 patterns to move -- 2 crafting and 1 stonecutter -- got "
                    + report.moved() + " (a pattern that will not resolve also reads as skipped)");
                return;
            }
            if (report.skippedExternal() != 2) {
                helper.fail("expected 2 processing patterns to be skipped, got "
                    + report.skippedExternal());
                return;
            }
            // And they are still THERE, not merely uncounted.
            if (inventory.getItem(1).isEmpty() || inventory.getItem(3).isEmpty()) {
                helper.fail("a processing pattern was taken out of the autocrafter anyway");
                return;
            }
            if (!inventory.getItem(0).isEmpty() || !inventory.getItem(2).isEmpty()) {
                helper.fail("a crafting pattern was left behind in the autocrafter");
                return;
            }
            if (!inventory.getItem(4).isEmpty()) {
                helper.fail("the stonecutter pattern was left behind -- the multiblock runs those,"
                    + " so the filter is matching on the wrong thing");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The foreign autocrafter's pattern inventory, by the same reflection the import uses.
     *
     * <p>Deliberately the same mechanism rather than a shortcut: if the module system ever blocks
     * it, this fails first and says so, instead of the import silently finding nothing and the test
     * reading as "moved 0".
     *
     * @return null if the field cannot be reached at all
     */
    @Nullable
    private static Container reachPatterns(final BlockEntity blockEntity) {
        for (Class<?> current = blockEntity.getClass(); current != null;
             current = current.getSuperclass()) {
            for (final java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (!com.refinedmods.refinedstorage.common.autocrafting.PatternInventory.class
                    .isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return (Container) field.get(blockEntity);
                } catch (final ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---- helpers ----

    /**
     * Builds a correct 3x3x4 shell at the template origin, leaving the interior empty.
     *
     * <p>Fills by the same rule the validator uses -- count how many coordinates sit at an extreme
     * -- rather than by a hand-written list of positions. A hand-written list would be a second
     * statement of the shape, and the two could disagree.
     */
    private static void buildShell(final GameTestHelper helper) {
        buildShell(helper, 0);
    }

    /**
     * The same 3x3x4 shell, shifted along x so a test can leave room beside it.
     *
     * <p>Needed because the Controller lands on the first wall slot the fill finds, which is on the
     * -x face -- so a test wanting to plug a cable into it has to keep that column free, and the
     * template has no negative coordinates.
     */
    private static void buildShell(final GameTestHelper helper, final int originX) {
        buildShellSized(helper, originX, originX + 2, 2, 3);
    }

    /** A shell of any size anchored at the origin, for tests that need a bigger interior. */
    private static void buildShellSized(final GameTestHelper helper,
                                        final int maxX, final int maxY, final int maxZ) {
        buildShellSized(helper, 0, maxX, maxY, maxZ);
    }

    private static void buildShellSized(final GameTestHelper helper, final int originX,
                                        final int maxX, final int maxY, final int maxZ) {
        final boolean[] controllerPlaced = {false};
        for (int x = originX; x <= maxX; x++) {
            for (int y = 0; y <= maxY; y++) {
                for (int z = 0; z <= maxZ; z++) {
                    int extremes = 0;
                    if (x == originX || x == maxX) {
                        extremes++;
                    }
                    if (y == 0 || y == maxY) {
                        extremes++;
                    }
                    if (z == 0 || z == maxZ) {
                        extremes++;
                    }
                    final Block block;
                    if (extremes >= 2) {
                        block = RsmcBlocks.FRAME.get();
                    } else if (extremes == 1) {
                        // First wall slot found becomes the Controller, so the shell is legal.
                        block = controllerPlaced[0] ? RsmcBlocks.CASING.get()
                            : RsmcBlocks.CONTROLLER.get();
                        controllerPlaced[0] = true;
                    } else {
                        block = null;
                    }
                    if (block != null) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    // ---- the Pattern Port ----
    //
    // These are the only tests the item handler has, and they have to be here rather than in a
    // headless suite: the handler is reached through a NeoForge block capability, which is a
    // registration in a live game and not something a plain JVM can stand up. A shape check
    // proving a Port fills a wall slot proves nothing about whether a hopper can find it.
    //
    // buildShell puts the Controller at (0,1,1) -- the first wall slot in its iteration order --
    // so (1,0,2) is a free wall slot in every one of these.

    /**
     * A pattern pushed into the Port ends up in a Pattern Storage block.
     *
     * <p>The whole feature in one test: capability registered, handler found, structure walked,
     * free slot located, pattern written where the screen and the Controller will both see it.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aPatternPipedIntoThePortLandsInStorage(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        final ItemStack leftover = handler.insertItem(0, encodedPattern(), false);
        if (!leftover.isEmpty()) {
            helper.fail("the port refused a pattern into a formed structure");
            return;
        }
        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2))
            instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no pattern storage where one was placed");
            return;
        }
        if (storage.patterns().getItem(0).isEmpty()) {
            helper.fail("the port accepted a pattern and it is not in the storage block");
            return;
        }
        helper.succeed();
    }

    /**
     * A processing pattern offered to the Port is refused, and stays in the pipe.
     *
     * <p>The automation door. A hopper does not read documentation, so "the multiblock does not do
     * processing patterns" has to be something the Port says rather than something the README says.
     *
     * <p>The assertion that matters is the second one: the stack comes <em>back</em>. A refusal that
     * consumed the pattern and dropped it would satisfy "it did not go in" and be far worse than
     * accepting it.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void thePortRefusesAProcessingPattern(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        final ItemStack offered = processingPattern();
        final ItemStack leftover = handler.insertItem(0, offered, false);
        if (leftover.isEmpty()) {
            helper.fail("the port took a processing pattern; it has no sink to run one with");
            return;
        }
        if (leftover.getCount() != offered.getCount()) {
            helper.fail("the port refused a processing pattern but did not hand all of it back:"
                + " offered " + offered.getCount() + ", returned " + leftover.getCount());
            return;
        }
        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2))
            instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no pattern storage where one was placed");
            return;
        }
        if (!storage.patterns().getItem(0).isEmpty()) {
            helper.fail("the port said no and put it in anyway");
            return;
        }
        helper.succeed();
    }

    /**
     * The pattern screen refuses a processing pattern, and still takes a crafting one.
     *
     * <p>The by-hand door, asked through the same {@link StructurePatterns} the menu's slots use.
     * Both halves matter: a filter that refuses everything would pass the first assertion, and the
     * bug it hides -- nobody can put any pattern in -- is worse than the one it fixes.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void thePatternScreenRefusesAProcessingPattern(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final StructurePatterns patterns =
            StructurePatterns.of(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)));
        if (patterns.getContainerSize() == 0) {
            helper.fail("no pattern slots in a formed structure");
            return;
        }
        if (patterns.canPlaceItem(0, processingPattern())) {
            helper.fail("a pattern slot accepted a processing pattern");
            return;
        }
        if (patterns.accepts(processingPattern())) {
            helper.fail("accepts() said yes to a processing pattern");
            return;
        }
        if (!patterns.canPlaceItem(0, craftingPattern())) {
            helper.fail("the filter refused a crafting pattern too -- it refuses everything");
            return;
        }
        helper.succeed();
    }

    /**
     * A processing pattern already sitting in storage is never handed to the network node.
     *
     * <p><strong>The backstop, and the only one of these three that helps an existing world.</strong>
     * The doors stop new ones arriving; they do nothing about a pattern a player put in before those
     * doors existed, which is still there on load and would still be advertised, planned and stalled.
     *
     * <p>It is written into the storage block directly, deliberately bypassing every filter, because
     * that is exactly the state a saved world can be in. What the test asserts is the two halves of
     * the chosen fix: <b>the node is not told about it</b>, and <b>the pattern is still in the slot
     * afterwards</b>. Deleting a player's pattern to tidy this up was never on the table.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void aProcessingPatternAlreadyInStorageIsNeverAdvertised(
        final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2))
            instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no pattern storage where one was placed");
            return;
        }
        // Straight past canPlaceItem, which is the point.
        storage.patterns().setItem(0, processingPattern());
        storage.patterns().setItem(1, craftingPattern());

        // Real ticks, not a loop of tickNode(). The node starts with zero pattern slots and only
        // grows them when refreshStateOccasionally rebuilds it from the structure -- which is on a
        // schedule, so driving the drain by hand pushes into an array that does not exist yet. The
        // first version of this test did exactly that and failed with "the node has 0 pattern
        // slots", which was the schedule being right and the test being in a hurry.
        helper.runAfterDelay(60L, () -> {
            final BlockPos controllerPos = new BlockPos(0, 1, 1);
            if (!(helper.getBlockEntity(controllerPos)
                instanceof ControllerBlockEntity controller)) {
                helper.fail("no Controller where buildShell puts one");
                return;
            }
            final Object[] pushed = nodePatterns(controller);
            if (pushed == null) {
                helper.fail("could not read the node's patterns by reflection -- NeoForge's module"
                    + " system is blocking it, and this test cannot see what it is asserting");
                return;
            }
            if (pushed.length < 2) {
                helper.fail("the node has " + pushed.length + " pattern slots; expected at"
                    + " least 2");
                return;
            }
            if (pushed[0] != null) {
                helper.fail("a processing pattern was pushed to the network node; a task planned"
                    + " against it would stall forever");
                return;
            }
            if (pushed[1] == null) {
                helper.fail("the crafting pattern beside it was not pushed either -- the filter is"
                    + " rejecting everything, not just processing patterns");
                return;
            }
            if (storage.patterns().getItem(0).isEmpty()) {
                helper.fail("the processing pattern was removed from the slot; it is supposed to"
                    + " stay where its owner left it, merely inert");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The node's pattern array, which RS keeps private and exposes no reader for.
     *
     * <p>Reflection rather than an accessor because there is no accessor:
     * {@code PatternProviderNetworkNode.patterns} is private with only {@code setPattern} to write
     * it. The alternative is asserting on something downstream -- what the network advertises as
     * craftable -- which needs a live network, energy and a cable, and would turn a test about one
     * filter into a test about half the mod.
     *
     * <p>The same approach {@code PatternImport} takes to reach an autocrafter's inventory, and it
     * works for the same reason. Returns null rather than throwing so the caller can say which of
     * the two things went wrong.
     */
    @Nullable
    private static Object[] nodePatterns(final ControllerBlockEntity controller) {
        try {
            final Field field = PatternProviderNetworkNode.class.getDeclaredField("patterns");
            field.setAccessible(true);
            return (Object[]) field.get(controller.node());
        } catch (final ReflectiveOperationException | RuntimeException e) {
            RSMBAC.LOGGER.error("[rsmbac] test could not read node patterns", e);
            return null;
        }
    }

    /**
     * A port filling a structure loses nothing and overwrites nothing.
     *
     * <p>The question this answers is the one asked before trusting a Port with a base's worth of
     * patterns: <em>can piping them in delete any?</em> A full storage block is filled one insert at
     * a time, every pattern distinguishable from every other, and then all of them are accounted
     * for -- so an insert that silently landed on top of an earlier one, or reported success while
     * writing nothing, shows up as a missing pattern rather than as a passing test.
     *
     * <p>The last insert is refused because the structure is full, which is the other direction that
     * must not lose anything: a refusal has to leave the stack with the pipe.
     */
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void aPortFillsAStorageWithoutLosingAPattern(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        final int capacity = StructurePower.PATTERNS_PER_STORAGE;
        for (int i = 0; i < capacity; i++) {
            final ItemStack leftover = handler.insertItem(0, encodedPattern(), false);
            if (!leftover.isEmpty()) {
                helper.fail("the port refused pattern " + i + " of " + capacity
                    + " into a structure that still had room");
                return;
            }
        }
        // Full now: the next one must come back rather than be swallowed.
        final ItemStack overflow = handler.insertItem(0, encodedPattern(), false);
        if (overflow.isEmpty()) {
            helper.fail("the port accepted a pattern into a full structure -- it was destroyed");
            return;
        }

        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2))
            instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no pattern storage where one was placed");
            return;
        }
        int found = 0;
        for (int i = 0; i < capacity; i++) {
            if (!storage.patterns().getItem(i).isEmpty()) {
                found++;
            }
        }
        if (found != capacity) {
            helper.fail("piped " + capacity + " patterns in and only " + found
                + " are there; " + (capacity - found) + " were lost or overwritten");
            return;
        }
        helper.succeed();
    }

    /**
     * The insert slot always reads empty, and that is a property worth pinning.
     *
     * <p>It is what stops a Refined Storage External Storage pointed at the Port from listing the
     * crafter's patterns as network items and handing them out. A handler that started reporting
     * its contents here would pass every other test in this file.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void thePortNeverShowsWhatItHolds(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        handler.insertItem(0, encodedPattern(), false);
        handler.insertItem(0, encodedPattern(), false);
        if (handler.getSlots() != 2) {
            helper.fail("expected two virtual slots, got " + handler.getSlots());
            return;
        }
        if (!handler.getStackInSlot(0).isEmpty()) {
            helper.fail("the insert slot is advertising its contents");
            return;
        }
        if (handler.getStackInSlot(1).isEmpty()) {
            helper.fail("the extract slot shows nothing after two patterns went in");
            return;
        }
        // One at a time, never the whole inventory: the extract slot is a window, not a listing.
        if (handler.getStackInSlot(1).getCount() != 1) {
            helper.fail("the extract slot is showing more than one pattern");
            return;
        }
        helper.succeed();
    }

    /** What goes in comes back out, last one first, and the rest stay put. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void thePortHandsBackTheLastPattern(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        handler.insertItem(0, encodedPattern(), false);
        handler.insertItem(0, encodedPattern(), false);
        if (handler.extractItem(1, 1, false).isEmpty()) {
            helper.fail("extracting from a port holding two patterns gave nothing");
            return;
        }
        if (!(helper.getBlockEntity(new BlockPos(1, 1, 2))
            instanceof PatternStorageBlockEntity storage)) {
            helper.fail("no pattern storage where one was placed");
            return;
        }
        if (!storage.patterns().getItem(1).isEmpty()) {
            helper.fail("extract took a pattern but slot 1 is still occupied");
            return;
        }
        if (storage.patterns().getItem(0).isEmpty()) {
            helper.fail("extract took the wrong one -- slot 0 should be untouched");
            return;
        }
        helper.succeed();
    }

    /**
     * An unformed structure refuses, rather than swallowing.
     *
     * <p>The direction that matters. A Port that accepted patterns into a half-built box would be
     * quietly destroying them, and nothing else in this file would notice.
     */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void anUnformedStructureRefusesPatterns(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());
        // No Pattern Storage anywhere, so the box is not a structure.

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        // A properly encoded one, so the refusal can only be about the structure. A blank pattern
        // is refused by RS's own filter, and this test would then pass without proving anything.
        final ItemStack offered = encodedPattern();
        final ItemStack leftover = handler.insertItem(0, offered, false);
        if (leftover.isEmpty() || leftover.getCount() != offered.getCount()) {
            helper.fail("an unformed structure swallowed a pattern");
            return;
        }
        helper.succeed();
    }

    /** Patterns and nothing else, whatever a pipe thinks it is doing. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void thePortTakesPatternsAndNothingElse(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_X).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());
        helper.setBlock(new BlockPos(1, 0, 2), RsmcBlocks.PORT.get());

        final IItemHandler handler = portHandler(helper, new BlockPos(1, 0, 2));
        if (handler == null) {
            helper.fail("no item handler capability on the Pattern Port");
            return;
        }
        final ItemStack cobble = new ItemStack(Items.COBBLESTONE, 8);
        if (handler.isItemValid(0, cobble)) {
            helper.fail("the port says it would take cobblestone");
            return;
        }
        if (handler.insertItem(0, cobble, false).getCount() != 8) {
            helper.fail("the port took cobblestone");
            return;
        }
        // And the extract slot is not an insert slot wearing a hat.
        if (handler.insertItem(1, encodedPattern(), false).isEmpty()) {
            helper.fail("the extract slot accepted an insert");
            return;
        }
        helper.succeed();
    }

    @Nullable
    private static IItemHandler portHandler(final GameTestHelper helper, final BlockPos relative) {
        return helper.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK, helper.absolutePos(relative), null);
    }

    /**
     * A pattern that a pattern slot will actually take.
     *
     * <p><strong>{@code new ItemStack(rsItem("pattern"))} is not one.</strong> RS's filter is
     * {@code PatternProviderItem.isValid}, which resolves the stack and asks whether a pattern came
     * back -- a blank pattern item resolves to nothing and is refused, which
     * {@link #onlyPatternsFitInPatternSlots} asserts on purpose. The first version of the Port
     * tests used a blank one and failed with "the port refused a pattern into a formed structure",
     * which was the Port being right and the test being wrong.
     *
     * <p><strong>This used to be a processing pattern, and no longer can be.</strong> It was one
     * because a processing pattern carries its own inputs and outputs and so resolves without any
     * particular recipe existing in whatever pack the tests run against -- a good reason, right up
     * until {@link com.wraithhawit.rsmbac.PatternPolicy} began refusing them at every door. A helper
     * named "a pattern a slot will take" that returns the one kind no slot takes is a suite that
     * fails for the right reason and reads like a bug.
     *
     * <p>So it is the crafting pattern now, and the recipe dependency it was avoiding is accepted --
     * as {@link #craftingPattern()} already accepted it. {@link #processingPattern()} is what to
     * reach for when a test wants the refused kind on purpose.
     */
    private static ItemStack encodedPattern() {
        return craftingPattern();
    }

    /**
     * A PROCESSING pattern: valid, resolvable, and refused by this mod everywhere.
     *
     * <p>Not a broken pattern and not a trick -- it is exactly what a player gets from a Pattern
     * Grid set to Processing, and it works perfectly in an autocrafter. The multiblock declines it
     * because it has no sink to push ingredients into, so a task planned against one would stall
     * forever. See {@link com.wraithhawit.rsmbac.PatternPolicy}.
     */
    private static ItemStack processingPattern() {
        final ItemStack stack = new ItemStack(rsItem("pattern"));
        stack.set(DataComponents.INSTANCE.getPatternState(),
            new PatternState(UUID.randomUUID(), PatternType.PROCESSING));
        stack.set(DataComponents.INSTANCE.getProcessingPatternState(), new ProcessingPatternState(
            List.of(Optional.of(new ProcessingPatternState.ProcessingIngredient(
                new ResourceAmount(new ItemResource(Items.COBBLESTONE), 1L), List.of()))),
            List.of(Optional.of(new ResourceAmount(new ItemResource(Items.STONE), 1L)))));
        return stack;
    }

    /**
     * A CRAFTING pattern, which the multiblock can actually run.
     *
     * <p>{@link #encodedPattern()} is deliberately a processing pattern, so that it resolves without
     * any recipe existing. This one has to be the other kind, because the point of the test using it
     * is the difference between the two -- and that means depending on a recipe. One oak log to four
     * oak planks is vanilla, shapeless, and present in every pack that has not deleted it.
     */
    private static ItemStack craftingPattern() {
        final ItemStack stack = new ItemStack(rsItem("pattern"));
        stack.set(DataComponents.INSTANCE.getPatternState(),
            new PatternState(UUID.randomUUID(), PatternType.CRAFTING));
        stack.set(DataComponents.INSTANCE.getCraftingPatternState(), new CraftingPatternState(
            false,
            CraftingInput.ofPositioned(1, 1, List.of(new ItemStack(Items.OAK_LOG)))));
        return stack;
    }

    /**
     * A STONECUTTER pattern, which the multiblock also runs.
     *
     * <p>Here because "processing patterns stay behind" is easy to over-apply into "only crafting
     * patterns move". Refined Storage builds crafting, stonecutter and smithing layouts with
     * {@code PatternLayout.internal} and only processing with {@code external}, so all three of the
     * first kind are ours to run. Asserting one of the less obvious two keeps that true if the
     * filter is ever rewritten.
     */
    private static ItemStack stonecutterPattern() {
        final ItemStack stack = new ItemStack(rsItem("pattern"));
        stack.set(DataComponents.INSTANCE.getPatternState(),
            new PatternState(UUID.randomUUID(), PatternType.STONECUTTER));
        stack.set(DataComponents.INSTANCE.getStonecutterPatternState(), new StonecutterPatternState(
            new ItemResource(Items.STONE), new ItemResource(Items.STONE_BRICKS)));
        return stack;
    }

    /** Runs the real detection against the real level, seeded at the structure's own corner. */
    private static Result find(final GameTestHelper helper) {
        final BlockPos corner = helper.absolutePos(new BlockPos(0, 0, 0));
        return MultiblockShape.find(new LevelBlockSource(helper.getLevel()),
            corner.getX(), corner.getY(), corner.getZ(), Config.maxStructureEdge);
    }

    private StructureGameTests() {
    }
}

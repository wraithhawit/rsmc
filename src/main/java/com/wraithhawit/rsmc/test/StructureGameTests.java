package com.wraithhawit.rsmc.test;

import java.util.List;

import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.block.ControllerBlock;
import com.wraithhawit.rsmc.block.ControllerState;
import com.wraithhawit.rsmc.content.RsmcBlocks;
import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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
 *   <li>{@link LevelBlockSource} reads them back correctly, including that a non-rsmc block reads
 *       as absent
 *   <li>the two halves agree -- a structure built out of the real blocks forms
 * </ul>
 *
 * <p>An 8x8x8 template, because the smallest legal structure is 3x3x4 and a 1x1x1 has nowhere to
 * build one.
 */
@GameTestHolder(RSMC.MODID)
@PrefixGameTestTemplate(false)
public final class StructureGameTests {
    /** The smallest legal structure, built from the real blocks, forms. */
    @GameTest(template = "empty8", timeoutTicks = 100)
    public static void smallestStructureForms(final GameTestHelper helper) {
        buildShell(helper);
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.FOUR_K).get());
        helper.setBlock(new BlockPos(1, 1, 2), RsmcBlocks.PATTERN_STORAGE.get());

        final Result result = find(helper);
        if (!result.formed()) {
            helper.fail("expected a structure, got " + result.failure());
            return;
        }
        if (result.stepsPerTick() != 4) {
            helper.fail("expected 4 steps/tick from one 4K CPU, got " + result.stepsPerTick());
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
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_K).get());
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
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_K).get());
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
        helper.setBlock(new BlockPos(1, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_K).get());
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
        helper.setBlock(new BlockPos(2, 1, 1), RsmcBlocks.CPUS.get(CpuTier.ONE_K).get());
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
        final boolean[] controllerPlaced = {false};
        for (int x = originX; x <= originX + 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 3; z++) {
                    int extremes = 0;
                    if (x == originX || x == originX + 2) {
                        extremes++;
                    }
                    if (y == 0 || y == 2) {
                        extremes++;
                    }
                    if (z == 0 || z == 3) {
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

    /** Runs the real detection against the real level, seeded at the structure's own corner. */
    private static Result find(final GameTestHelper helper) {
        final BlockPos corner = helper.absolutePos(new BlockPos(0, 0, 0));
        return MultiblockShape.find(new LevelBlockSource(helper.getLevel()),
            corner.getX(), corner.getY(), corner.getZ());
    }

    private StructureGameTests() {
    }
}

package com.wraithhawit.rsmc.test;

import java.util.List;

import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.block.ControllerBlock;
import com.wraithhawit.rsmc.block.ControllerState;
import com.wraithhawit.rsmc.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmc.content.RsmcBlocks;
import com.wraithhawit.rsmc.menu.StructurePatterns;
import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.StructurePower;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
     * <p>Nothing about rsmc was denying it. RS runs a global {@code BlockEvent.BreakEvent} handler
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

    /** Runs the real detection against the real level, seeded at the structure's own corner. */
    private static Result find(final GameTestHelper helper) {
        final BlockPos corner = helper.absolutePos(new BlockPos(0, 0, 0));
        return MultiblockShape.find(new LevelBlockSource(helper.getLevel()),
            corner.getX(), corner.getY(), corner.getZ());
    }

    private StructureGameTests() {
    }
}

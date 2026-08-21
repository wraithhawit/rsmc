package com.wraithhawit.rsmc.test;

import java.util.List;

import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.content.RsmcBlocks;
import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
     * check that merely looks for missing files notices. 0.4.0 shipped exactly that way.
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

    // ---- helpers ----

    /**
     * Builds a correct 3x3x4 shell at the template origin, leaving the interior empty.
     *
     * <p>Fills by the same rule the validator uses -- count how many coordinates sit at an extreme
     * -- rather than by a hand-written list of positions. A hand-written list would be a second
     * statement of the shape, and the two could disagree.
     */
    private static void buildShell(final GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 3; z++) {
                    int extremes = 0;
                    if (x == 0 || x == 2) {
                        extremes++;
                    }
                    if (y == 0 || y == 2) {
                        extremes++;
                    }
                    if (z == 0 || z == 3) {
                        extremes++;
                    }
                    final Block block = extremes >= 2 ? RsmcBlocks.FRAME.get()
                        : extremes == 1 ? RsmcBlocks.CASING.get() : null;
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

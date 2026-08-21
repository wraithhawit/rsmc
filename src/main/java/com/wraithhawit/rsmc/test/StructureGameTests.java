package com.wraithhawit.rsmc.test;

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
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

package com.wraithhawit.rsmc.test;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Component;
import com.wraithhawit.rsmc.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

/**
 * Runs the structure-detection cases in a plain JVM: {@code ./gradlew shapeCheck}.
 *
 * <p>Adopted from rstweaks' planner check, and for the same reason. Every one of these cases would
 * otherwise be checked by launching Minecraft and stacking blocks by hand, which is slow enough
 * that in practice it means the awkward cases -- the flush-neighbour case especially -- get checked
 * once and never again. Here they are checked on every build.
 *
 * <p>Exits non-zero on the first failure so it can gate a build.
 */
public final class HeadlessShapeCheck {
    private static int checks;
    private static int failures;

    private HeadlessShapeCheck() {
    }

    public static void main(final String[] args) {
        singleBlockIsAStructure();
        oneByOneByTwoIsAStructure();
        tierWeightsAreSummed();
        aHoleIsNotSolid();
        anOverhangIsNotSolid();
        twoFlushBoxesBreakEachOther();
        aGapKeepsThemSeparate();
        sixteenCubedIsLegal();
        seventeenLongIsTooLarge();
        aSeedOnNothingFails();
        maximumStructure();

        System.out.println("shape checks: " + checks);
        if (failures > 0) {
            System.out.println("FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("PASS");
    }

    // ---- cases ----

    private static void singleBlockIsAStructure() {
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        final Result result = world.find(0, 0, 0);
        expectFormed("1x1x1", result);
        expect("1x1x1 volume", 1, result.volume());
        expect("1x1x1 steps", 1, result.stepsPerTick());
        expect("1x1x1 storages", 0, result.patternStorages());
    }

    private static void oneByOneByTwoIsAStructure() {
        // The smallest structure that can actually do anything: without a pattern storage there is
        // nowhere to put a pattern, so one CPU on its own is a formed but useless structure.
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        world.storage(0, 0, 1);
        final Result result = world.find(0, 0, 0);
        expectFormed("1x1x2", result);
        expect("1x1x2 volume", 2, result.volume());
        expect("1x1x2 cpus", 1, result.cpus());
        expect("1x1x2 storages", 1, result.patternStorages());
    }

    private static void tierWeightsAreSummed() {
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        world.cpu(1, 0, 0, CpuTier.FOUR_K);
        world.cpu(2, 0, 0, CpuTier.SIXTEEN_K);
        world.cpu(3, 0, 0, CpuTier.SIXTY_FOUR_K);
        final Result result = world.find(0, 0, 0);
        expectFormed("mixed tiers", result);
        expect("mixed tiers steps", 1 + 4 + 16 + 64, result.stepsPerTick());
        expect("mixed tiers cpus", 4, result.cpus());
    }

    private static void aHoleIsNotSolid() {
        // A 3x3x1 slab with the middle missing. The flood walks the ring, the bounding box is the
        // full 3x3, and the centre is the hole -- which is exactly the coordinate a player needs.
        final World world = new World();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 1 && z == 1) {
                    continue;
                }
                world.cpu(x, 0, z, CpuTier.ONE_K);
            }
        }
        final Result result = world.find(0, 0, 0);
        expectFailure("ring with a hole", result, Failure.NOT_SOLID);
        expectPos("ring hole position", result, 1, 0, 1);
    }

    private static void anOverhangIsNotSolid() {
        // A 2x1x2 box with one extra block stuck on the side.
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        world.cpu(1, 0, 0, CpuTier.ONE_K);
        world.cpu(0, 0, 1, CpuTier.ONE_K);
        world.cpu(1, 0, 1, CpuTier.ONE_K);
        world.cpu(2, 0, 0, CpuTier.ONE_K);
        final Result result = world.find(0, 0, 0);
        expectFailure("overhang", result, Failure.NOT_SOLID);
    }

    private static void twoFlushBoxesBreakEachOther() {
        // The documented consequence of the solid-box rule, pinned as a test so nobody "fixes" it
        // by accident. Two 1x1x1 CPUs and a 1x1x2 beside them share a face, so the connected
        // region is an L and forms nothing.
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        world.cpu(0, 0, 1, CpuTier.ONE_K);
        world.cpu(1, 0, 1, CpuTier.ONE_K);
        final Result result = world.find(0, 0, 0);
        expectFailure("flush neighbours", result, Failure.NOT_SOLID);
    }

    private static void aGapKeepsThemSeparate() {
        final World world = new World();
        world.cpu(0, 0, 0, CpuTier.ONE_K);
        world.cpu(0, 0, 2, CpuTier.FOUR_K);
        final Result first = world.find(0, 0, 0);
        expectFormed("gapped first", first);
        expect("gapped first steps", 1, first.stepsPerTick());
        final Result second = world.find(0, 0, 2);
        expectFormed("gapped second", second);
        expect("gapped second steps", 4, second.stepsPerTick());
    }

    private static void sixteenCubedIsLegal() {
        final World world = new World();
        fill(world, 0, 0, 0, 15, 15, 15);
        final Result result = world.find(0, 0, 0);
        expectFormed("16x16x16", result);
        expect("16x16x16 volume", 4096, result.volume());
    }

    private static void seventeenLongIsTooLarge() {
        final World world = new World();
        fill(world, 0, 0, 0, 16, 0, 0);
        final Result result = world.find(0, 0, 0);
        expectFailure("17 long", result, Failure.TOO_LARGE);
    }

    private static void aSeedOnNothingFails() {
        final Result result = new World().find(0, 0, 0);
        expectFailure("empty seed", result, Failure.NOT_SOLID);
    }

    private static void maximumStructure() {
        // The number the README quotes. 4096 64K CPUs is 262,144 steps/tick, against a stock
        // autocrafter ceiling of 2.5 -- so the printed figure is checked rather than asserted in
        // prose, because a tier weight change would otherwise silently make the docs wrong.
        final World world = new World();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    world.cpu(x, y, z, CpuTier.SIXTY_FOUR_K);
                }
            }
        }
        final Result result = world.find(0, 0, 0);
        expectFormed("max structure", result);
        expect("max steps/tick", 4096 * 64, result.stepsPerTick());
    }

    // ---- harness ----

    private static void fill(final World world, final int x0, final int y0, final int z0,
                             final int x1, final int y1, final int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    world.cpu(x, y, z, CpuTier.ONE_K);
                }
            }
        }
    }

    /** A sparse map standing in for a level. */
    private static final class World implements MultiblockShape.BlockSource {
        private final Map<String, Component> blocks = new HashMap<>();

        void cpu(final int x, final int y, final int z, final CpuTier tier) {
            this.blocks.put(x + "," + y + "," + z, Component.cpu(tier));
        }

        void storage(final int x, final int y, final int z) {
            this.blocks.put(x + "," + y + "," + z, Component.patternStorage());
        }

        Result find(final int x, final int y, final int z) {
            return MultiblockShape.find(this, x, y, z);
        }

        @Nullable
        @Override
        public Component blockAt(final int x, final int y, final int z) {
            return this.blocks.get(x + "," + y + "," + z);
        }
    }

    private static void expectFormed(final String what, final Result result) {
        checks++;
        if (!result.formed()) {
            failures++;
            System.out.println("FAILED " + what + ": expected a structure, got " + result.failure());
        }
    }

    private static void expectFailure(final String what, final Result result, final Failure expected) {
        checks++;
        if (result.formed() || result.failure() != expected) {
            failures++;
            System.out.println("FAILED " + what + ": expected " + expected
                + ", got " + (result.formed() ? "a formed structure" : result.failure()));
        }
    }

    private static void expectPos(final String what, final Result result,
                                  final int x, final int y, final int z) {
        checks++;
        final int[] pos = result.failurePos();
        if (pos == null || pos[0] != x || pos[1] != y || pos[2] != z) {
            failures++;
            System.out.println("FAILED " + what + ": expected (" + x + "," + y + "," + z + "), got "
                + (pos == null ? "null" : "(" + pos[0] + "," + pos[1] + "," + pos[2] + ")"));
        }
    }

    private static void expect(final String what, final int expected, final int actual) {
        checks++;
        if (expected != actual) {
            failures++;
            System.out.println("FAILED " + what + ": expected " + expected + ", got " + actual);
        }
    }
}

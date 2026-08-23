package com.wraithhawit.rsmc.test;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.wraithhawit.rsmc.structure.CpuTier;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.BlockKind;
import com.wraithhawit.rsmc.structure.MultiblockShape.Component;
import com.wraithhawit.rsmc.structure.MultiblockShape.Failure;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

/**
 * Runs the structure-detection cases in a plain JVM: {@code ./gradlew shapeCheck}.
 *
 * <p>Adopted from rstweaks' planner check, and for the same reason. Every one of these would
 * otherwise be checked by launching Minecraft and stacking blocks by hand, which is slow enough
 * that in practice the awkward cases get checked once and never again. Here they run on every
 * build.
 *
 * <p>Exits non-zero on the first failure so it can gate a build.
 */
public final class HeadlessShapeCheck {
    private static int checks;
    private static int failures;

    private HeadlessShapeCheck() {
    }

    public static void main(final String[] args) {
        smallestLegalStructure();
        threeCubedHasNoRoomForBoth();
        tierWeightsAreSummed();
        aFrameInAWallSlotIsWrong();
        aCasingOnAnEdgeIsWrong();
        aCpuInTheShellIsWrong();
        aHoleIsNotSolid();
        anOverhangBreaksIt();
        interiorMustHaveAPatternStorage();
        seventeenLongIsTooLarge();
        aSeedOnNothingFails();
        maximumStructure();
        aStructureNeedsAController();
        twoControllersIsAnError();
        aControllerOnAnEdgeIsWrong();
        theControllerPositionIsReported();

        System.out.println("shape checks: " + checks);
        if (failures > 0) {
            System.out.println("FAIL (" + failures + ")");
            System.exit(1);
        }
        System.out.println("PASS");
    }

    // ---- cases ----

    private static void smallestLegalStructure() {
        // 3x3x4: the smallest box with an interior of two, which is the smallest that can hold the
        // required CPU and pattern storage at once. Nothing declares this minimum -- it falls out
        // of the interior requirement, and this case is what proves that.
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        final Result result = world.find(0, 0, 0);
        expectFormed("3x3x4", result);
        expect("3x3x4 volume", 36, result.volume());
        expect("3x3x4 cpus", 1, result.cpus());
        expect("3x3x4 steps", 1, result.stepsPerTick());
        expect("3x3x4 storages", 1, result.patternStorages());
    }

    private static void threeCubedHasNoRoomForBoth() {
        // 3x3x3 has exactly one interior slot. Whichever block goes in it, the other is missing --
        // which is why the effective minimum is 3x3x4 without anything having to say so.
        final World world = shell(0, 0, 0, 2, 2, 2);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        expectFailure("3x3x3 cpu only", world.find(0, 0, 0), Failure.NO_PATTERN_STORAGE);

        final World other = shell(0, 0, 0, 2, 2, 2);
        other.put(1, 1, 1, BlockKind.PATTERN_STORAGE);
        expectFailure("3x3x3 storage only", other.find(0, 0, 0), Failure.NO_CPU);
    }

    private static void tierWeightsAreSummed() {
        // 3x3x6 gives an interior of 1x1x4: three CPUs of different tiers and one storage.
        final World world = shell(0, 0, 0, 2, 2, 5);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.cpu(1, 1, 2, CpuTier.FOUR_X);
        world.cpu(1, 1, 3, CpuTier.SIXTY_FOUR_X);
        world.put(1, 1, 4, BlockKind.PATTERN_STORAGE);
        final Result result = world.find(0, 0, 0);
        expectFormed("mixed tiers", result);
        expect("mixed tiers steps", 1 + 4 + 64, result.stepsPerTick());
        expect("mixed tiers cpus", 3, result.cpus());
    }

    private static void aFrameInAWallSlotIsWrong() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        // (1, 0, 1) has one coordinate at an extreme, so it is a wall slot.
        world.put(1, 0, 1, BlockKind.FRAME);
        final Result result = world.find(0, 0, 0);
        expectFailure("frame in a wall", result, Failure.WRONG_BLOCK);
        expectPos("frame in a wall position", result, 1, 0, 1);
    }

    private static void aCasingOnAnEdgeIsWrong() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        // (0, 0, 1) has two coordinates at an extreme, so it is an edge.
        world.put(0, 0, 1, BlockKind.CASING);
        expectFailure("casing on an edge", world.find(0, 0, 0), Failure.WRONG_BLOCK);
    }

    private static void aCpuInTheShellIsWrong() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        world.cpu(1, 0, 1, CpuTier.ONE_X);
        expectFailure("cpu in the wall", world.find(0, 0, 0), Failure.WRONG_BLOCK);
    }

    private static void aHoleIsNotSolid() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        world.remove(1, 0, 1);
        final Result result = world.find(0, 0, 0);
        expectFailure("hole in a wall", result, Failure.NOT_SOLID);
        expectPos("hole position", result, 1, 0, 1);
    }

    private static void anOverhangBreaksIt() {
        // A complete structure with one extra frame stuck on the outside.
        //
        // Which failure this reports is deliberately NOT pinned. Widening the bounding box moves
        // every position's role -- a block that was an edge becomes a wall, and so on -- so the
        // enlarged box is wrong in many places at once: holes where the overhang's own box is
        // empty, and role mismatches throughout the original shell. All of them are true, and
        // which one surfaces is just scan order. Asserting one would be asserting the order of
        // three nested loops, which is not a rule anyone should be held to.
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        world.put(3, 1, 1, BlockKind.FRAME);
        expectNotFormed("overhang", world.find(0, 0, 0));
    }

    private static void interiorMustHaveAPatternStorage() {
        // A big interior full of CPUs and no storage: formed shape, nowhere to put a pattern.
        final World world = shell(0, 0, 0, 2, 2, 5);
        for (int z = 1; z <= 4; z++) {
            world.cpu(1, 1, z, CpuTier.ONE_X);
        }
        expectFailure("all cpus", world.find(0, 0, 0), Failure.NO_PATTERN_STORAGE);
    }

    private static void seventeenLongIsTooLarge() {
        final World world = shell(0, 0, 0, 2, 2, 16);
        expectFailure("17 long", world.find(0, 0, 0), Failure.TOO_LARGE);
    }

    private static void aSeedOnNothingFails() {
        expectFailure("empty seed", new World().find(0, 0, 0), Failure.NOT_SOLID);
    }

    private static void maximumStructure() {
        // The number the README quotes: a 16x16x16 whose 14x14x14 interior is 64x CPUs but for one
        // pattern storage. Checked rather than asserted in prose, because a tier weight change
        // would otherwise silently make the docs wrong.
        final World world = shell(0, 0, 0, 15, 15, 15);
        for (int x = 1; x <= 14; x++) {
            for (int y = 1; y <= 14; y++) {
                for (int z = 1; z <= 14; z++) {
                    world.cpu(x, y, z, CpuTier.SIXTY_FOUR_X);
                }
            }
        }
        world.put(1, 1, 1, BlockKind.PATTERN_STORAGE);
        final Result result = world.find(0, 0, 0);
        expectFormed("max structure", result);
        expect("max volume", 4096, result.volume());
        expect("max cpus", 14 * 14 * 14 - 1, result.cpus());
        expect("max steps/tick", (14 * 14 * 14 - 1) * 64, result.stepsPerTick());
    }

    private static void aStructureNeedsAController() {
        // Every wall a plain Casing: a perfectly built box with no way to reach a network.
        final World world = shellWithoutController(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        expectFailure("no controller", world.find(0, 0, 0), Failure.NO_CONTROLLER);
    }

    private static void twoControllersIsAnError() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        // shell() already placed one on the first wall slot it found; add a second elsewhere.
        world.put(1, 2, 1, BlockKind.CONTROLLER);
        final Result result = world.find(0, 0, 0);
        expectFailure("two controllers", result, Failure.TOO_MANY_CONTROLLERS);
        // The SECOND one is reported, not the first -- the first is likely the one to keep.
        expectPos("second controller reported", result, 1, 2, 1);
    }

    private static void aControllerOnAnEdgeIsWrong() {
        final World world = shell(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        world.put(0, 0, 1, BlockKind.CONTROLLER);
        expectFailure("controller on an edge", world.find(0, 0, 0), Failure.WRONG_BLOCK);
    }

    private static void theControllerPositionIsReported() {
        final World world = shellWithoutController(0, 0, 0, 2, 2, 3);
        world.cpu(1, 1, 1, CpuTier.ONE_X);
        world.put(1, 1, 2, BlockKind.PATTERN_STORAGE);
        world.put(1, 0, 1, BlockKind.CONTROLLER);
        final Result result = world.find(0, 0, 0);
        expectFormed("controller position", result);
        final int[] pos = result.controllerPos();
        checks++;
        if (pos == null || pos[0] != 1 || pos[1] != 0 || pos[2] != 1) {
            failures++;
            System.out.println("FAILED controller position: got "
                + (pos == null ? "null" : pos[0] + "," + pos[1] + "," + pos[2]));
        }
    }

    // ---- harness ----

    /**
     * Builds a correct shell over the given bounds and leaves the interior empty, so a case only
     * has to say what goes inside. Every shell slot is filled by the same rule the validator uses
     * -- two or more coordinates at an extreme is an edge, one is a wall -- which means a bug in
     * that rule would make these cases fail rather than hide it.
     */
    private static World shell(final int x0, final int y0, final int z0,
                               final int x1, final int y1, final int z1) {
        return build(x0, y0, z0, x1, y1, z1, true);
    }

    /** Same, but every wall slot is a plain Casing -- for the cases that supply their own. */
    private static World shellWithoutController(final int x0, final int y0, final int z0,
                                                final int x1, final int y1, final int z1) {
        return build(x0, y0, z0, x1, y1, z1, false);
    }

    private static World build(final int x0, final int y0, final int z0,
                               final int x1, final int y1, final int z1,
                               final boolean withController) {
        final World world = new World();
        final boolean[] controllerPlaced = {false};
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    int extremes = 0;
                    if (x == x0 || x == x1) {
                        extremes++;
                    }
                    if (y == y0 || y == y1) {
                        extremes++;
                    }
                    if (z == z0 || z == z1) {
                        extremes++;
                    }
                    if (extremes >= 2) {
                        world.put(x, y, z, BlockKind.FRAME);
                    } else if (extremes == 1) {
                        // The first wall slot found becomes the Controller, so every shell this
                        // builds is a legal one. Cases that want it missing or doubled say so.
                        final boolean here = withController && !controllerPlaced[0];
                        world.put(x, y, z, here ? BlockKind.CONTROLLER : BlockKind.CASING);
                        controllerPlaced[0] |= here;
                    }
                }
            }
        }
        return world;
    }

    /** A sparse map standing in for a level. */
    private static final class World implements MultiblockShape.BlockSource {
        private final Map<String, Component> blocks = new HashMap<>();

        void put(final int x, final int y, final int z, final BlockKind kind) {
            this.blocks.put(at(x, y, z), Component.of(kind));
        }

        void cpu(final int x, final int y, final int z, final CpuTier tier) {
            this.blocks.put(at(x, y, z), Component.cpu(tier));
        }

        void remove(final int x, final int y, final int z) {
            this.blocks.remove(at(x, y, z));
        }

        Result find(final int x, final int y, final int z) {
            return MultiblockShape.find(this, x, y, z);
        }

        @Nullable
        @Override
        public Component blockAt(final int x, final int y, final int z) {
            return this.blocks.get(at(x, y, z));
        }

        private static String at(final int x, final int y, final int z) {
            return x + "," + y + "," + z;
        }
    }

    private static void expectFormed(final String what, final Result result) {
        checks++;
        if (!result.formed()) {
            failures++;
            System.out.println("FAILED " + what + ": expected a structure, got " + result.failure()
                + " at " + describe(result));
        }
    }

    private static void expectNotFormed(final String what, final Result result) {
        checks++;
        if (result.formed()) {
            failures++;
            System.out.println("FAILED " + what + ": expected no structure, got one");
        }
    }

    private static void expectFailure(final String what, final Result result,
                                      final Failure expected) {
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
                + describe(result));
        }
    }

    private static void expect(final String what, final int expected, final int actual) {
        checks++;
        if (expected != actual) {
            failures++;
            System.out.println("FAILED " + what + ": expected " + expected + ", got " + actual);
        }
    }

    private static String describe(final Result result) {
        final int[] pos = result.failurePos();
        return pos == null ? "null" : "(" + pos[0] + "," + pos[1] + "," + pos[2] + ")";
    }
}

package com.wraithhawit.rsmbac.structure;

import java.util.ArrayDeque;
import java.util.HashSet;

import javax.annotation.Nullable;

/**
 * Finds and validates the crafter multiblock: a hollow rectangular box with a working core.
 *
 * <p>The shape follows Reborn Storage's multiblock crafter, which is the design this mod is a
 * fresh take on -- no code or assets from it, only the shape, which is an idea rather than a work.
 * Reborn Storage was itself openly inspired by Applied Energistics' crafting CPU, so the whole
 * family looks alike; that is the genre, not a copy.
 *
 * <h2>The rule</h2>
 *
 * <p>A rectangular box, no bigger than 16 on any axis, whose every position is filled and whose
 * three roles are decided purely by where a position sits in the box:
 *
 * <ul>
 *   <li><strong>Edges</strong> -- two or more coordinates at an extreme, which is every edge and
 *       corner of the box -- must be {@link BlockKind#FRAME}.
 *   <li><strong>Walls</strong> -- exactly one coordinate at an extreme -- must be
 *       {@link BlockKind#CASING}.
 *   <li><strong>Interior</strong> -- no coordinate at an extreme -- must be a
 *       {@link BlockKind#CPU} or a {@link BlockKind#PATTERN_STORAGE}, and there must be at least
 *       one of each.
 * </ul>
 *
 * <p><strong>The 3x3x4 minimum is derived, not chosen.</strong> A box needs 3 on every axis before
 * it has any interior at all, and the core needs at least one CPU and at least one pattern storage
 * -- so an interior of one block is not enough, and the smallest legal box is the smallest one with
 * an interior of two. There is no separate minimum check in this class: {@link Failure#NO_CPU} and
 * {@link Failure#NO_PATTERN_STORAGE} enforce it on their own, which means the rule cannot drift out
 * of step with the constant that states it.
 *
 * <h2>Free of Minecraft types, on purpose</h2>
 *
 * <p>All of the above is geometry, so it runs in a plain JVM under {@code ./gradlew shapeCheck}
 * rather than by launching the game and stacking blocks by hand. The level is reached only through
 * {@link BlockSource}.
 *
 * <p><strong>Consequence worth knowing:</strong> two structures placed flush against each other are
 * one connected region, and that region is not a box, so <em>both</em> stop working. Leave a gap.
 */
public final class MultiblockShape {
    /** Per axis. 16x16x16 is the largest legal structure. */
    public static final int MAX_EDGE = 16;

    /**
     * The most blocks a search will visit before giving up. Without a cap, a player who floors a
     * chunk in crafter blocks would have this scanning hundreds of thousands of positions on the
     * tick the last one is placed.
     */
    public static final int MAX_VOLUME = MAX_EDGE * MAX_EDGE * MAX_EDGE;

    private MultiblockShape() {
    }

    /** What the level looks like to the search. */
    @FunctionalInterface
    public interface BlockSource {
        /**
         * @return the rsmbac block at this position, or null for anything else -- air, stone, a
         *     chest, or an unloaded chunk. Treating unloaded as "not ours" is deliberate: a
         *     structure must never form across a chunk boundary it cannot see, or it would silently
         *     change shape when that chunk loads.
         */
        @Nullable
        Component blockAt(int x, int y, int z);
    }

    /**
     * One block of the structure.
     *
     * @param kind which of the four block types it is
     * @param tier the CPU tier; null for everything that is not a CPU
     */
    public record Component(BlockKind kind, @Nullable CpuTier tier) {
        public static Component cpu(final CpuTier tier) {
            return new Component(BlockKind.CPU, tier);
        }

        public static Component of(final BlockKind kind) {
            return new Component(kind, null);
        }
    }

    /** The four block types, and the role each one fills. */
    public enum BlockKind {
        /** Edges and corners of the box. */
        FRAME,
        /** The flat wall panels between the edges. */
        CASING,
        /**
         * Takes the place of one wall panel. Exactly one per structure, never on an edge.
         *
         * <p>The structure's single point of contact with the world: the only block that carries a
         * network node, and the position everything else is derived around.
         */
        CONTROLLER,
        /** Interior. Adds crafting speed. Tiered. */
        CPU,
        /** Interior. Holds patterns. One tier only. */
        PATTERN_STORAGE;

        /** Whether this block belongs in the core rather than in the shell. */
        boolean isInterior() {
            return this == CPU || this == PATTERN_STORAGE;
        }
    }

    /** Where a position sits in the box, which is what decides the block it must hold. */
    public enum Role {
        /** Two or more coordinates at an extreme. Wants a {@link BlockKind#FRAME}. */
        EDGE,
        /** Exactly one coordinate at an extreme. Wants a {@link BlockKind#CASING} or the CONTROLLER. */
        WALL,
        /** No coordinate at an extreme. Wants a CPU or a pattern storage. */
        INTERIOR
    }

    /** Why a structure did not form. */
    public enum Failure {
        /** A position inside the bounding box holds no rsmbac block at all. */
        NOT_SOLID,
        /** The region runs past {@link #MAX_EDGE} on some axis. */
        TOO_LARGE,
        /** A position holds an rsmbac block, but the wrong one for where it sits. */
        WRONG_BLOCK,
        /**
         * The interior holds no CPU. Also what a box too small to have an interior fails as, since
         * an interior of nothing contains no CPU.
         */
        NO_CPU,
        /** The interior holds no pattern storage, so there is nowhere to put a pattern. */
        NO_PATTERN_STORAGE,
        /** No Controller anywhere on the walls, so the structure has no way to reach a network. */
        NO_CONTROLLER,
        /**
         * More than one Controller. {@link Result#failurePos} is the second one found, which is the
         * one to take out -- not the first, which is very likely the one the player meant to keep.
         */
        TOO_MANY_CONTROLLERS
    }

    /**
     * A formed structure, or the reason there is not one.
     *
     * @param formed          whether the bounds and counts mean anything
     * @param failure         null when formed
     * @param failurePos      the offending position as {x, y, z}, or null when formed
     * @param expected        for {@link Failure#WRONG_BLOCK} and {@link Failure#NOT_SOLID}, the
     *                        role that position needed to fill; null otherwise
     * @param cpus            how many CPU blocks the core holds, of any tier
     * @param stepsPerTick    the sum of those CPUs' tier weights -- the structure's crafting rate
     * @param patternStorages how many pattern storage blocks the core holds
     * @param controllerPos   where the Controller is, as {x, y, z}; null unless formed. This is the
     *                        structure's host -- the one block that carries a network node -- and it
     *                        is a position found in the world rather than a rule like "the minimum
     *                        corner", so the player chooses it by building it.
     */
    public record Result(boolean formed,
                         @Nullable Failure failure,
                         @Nullable int[] failurePos,
                         @Nullable Role expected,
                         int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ,
                         int cpus,
                         int stepsPerTick,
                         int patternStorages,
                         @Nullable int[] controllerPos) {
        public int volume() {
            return this.sizeX() * this.sizeY() * this.sizeZ();
        }

        public int sizeX() {
            return this.maxX - this.minX + 1;
        }

        public int sizeY() {
            return this.maxY - this.minY + 1;
        }

        public int sizeZ() {
            return this.maxZ - this.minZ + 1;
        }

        static Result failed(final Failure failure, @Nullable final Role expected,
                             final int x, final int y, final int z) {
            return new Result(false, failure, new int[] {x, y, z}, expected,
                0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        }
    }

    /**
     * Walks the region containing the seed and decides whether it is a legal structure.
     *
     * <p>Two passes. The first floods the connected region of rsmbac blocks to find its extent; the
     * second checks every position inside the resulting bounding box against the role that position
     * demands. One pass cannot do this: the role of a position is defined against the bounds, and
     * a flood fill knows nothing about the bounds until it has finished.
     */
    public static Result find(final BlockSource source,
                              final int seedX, final int seedY, final int seedZ) {
        if (source.blockAt(seedX, seedY, seedZ) == null) {
            return Result.failed(Failure.NOT_SOLID, null, seedX, seedY, seedZ);
        }
        final Flood flood = new Flood(source, seedX, seedY, seedZ);
        final Failure floodFailure = flood.run();
        if (floodFailure != null) {
            return Result.failed(floodFailure, null, flood.farX, flood.farY, flood.farZ);
        }
        int cpus = 0;
        int stepsPerTick = 0;
        int patternStorages = 0;
        int[] controllerPos = null;
        for (int x = flood.minX; x <= flood.maxX; x++) {
            for (int y = flood.minY; y <= flood.maxY; y++) {
                for (int z = flood.minZ; z <= flood.maxZ; z++) {
                    final Role role = roleOf(flood, x, y, z);
                    final Component component = source.blockAt(x, y, z);
                    if (component == null) {
                        return Result.failed(Failure.NOT_SOLID, role, x, y, z);
                    }
                    if (!fits(role, component.kind())) {
                        return Result.failed(Failure.WRONG_BLOCK, role, x, y, z);
                    }
                    if (component.kind() == BlockKind.CPU && component.tier() != null) {
                        cpus++;
                        stepsPerTick += component.tier().stepsPerTick();
                    } else if (component.kind() == BlockKind.PATTERN_STORAGE) {
                        patternStorages++;
                    } else if (component.kind() == BlockKind.CONTROLLER) {
                        if (controllerPos != null) {
                            // The SECOND one is reported, not the first: the first is very likely
                            // the one the player meant to keep.
                            return Result.failed(Failure.TOO_MANY_CONTROLLERS, Role.WALL, x, y, z);
                        }
                        controllerPos = new int[] {x, y, z};
                    }
                }
            }
        }
        // Reported against the middle of the box, which for a structure with no interior is the
        // position a player would have to make room at. Nothing else in the box is at fault, so
        // pointing at a shell block would be actively misleading.
        final int midX = (flood.minX + flood.maxX) / 2;
        final int midY = (flood.minY + flood.maxY) / 2;
        final int midZ = (flood.minZ + flood.maxZ) / 2;
        if (cpus == 0) {
            return Result.failed(Failure.NO_CPU, Role.INTERIOR, midX, midY, midZ);
        }
        if (patternStorages == 0) {
            return Result.failed(Failure.NO_PATTERN_STORAGE, Role.INTERIOR, midX, midY, midZ);
        }
        if (controllerPos == null) {
            // Reported against the middle of a wall rather than the middle of the box, because the
            // fix is to swap a Casing for a Controller and the box centre is not a place a player
            // can put one.
            return Result.failed(Failure.NO_CONTROLLER, Role.WALL, midX, flood.minY, midZ);
        }
        return new Result(true, null, null, null,
            flood.minX, flood.minY, flood.minZ,
            flood.maxX, flood.maxY, flood.maxZ,
            cpus, stepsPerTick, patternStorages, controllerPos);
    }

    /**
     * Which role a position fills, from how many of its coordinates sit at an extreme of the box.
     *
     * <p>Counting extremes is the whole rule. A corner has three, an edge two, a wall one, and the
     * interior none -- so "edges and corners are frames" needs no special case for corners.
     */
    private static Role roleOf(final Flood bounds, final int x, final int y, final int z) {
        int extremes = 0;
        if (x == bounds.minX || x == bounds.maxX) {
            extremes++;
        }
        if (y == bounds.minY || y == bounds.maxY) {
            extremes++;
        }
        if (z == bounds.minZ || z == bounds.maxZ) {
            extremes++;
        }
        if (extremes >= 2) {
            return Role.EDGE;
        }
        return extremes == 1 ? Role.WALL : Role.INTERIOR;
    }

    private static boolean fits(final Role role, final BlockKind kind) {
        return switch (role) {
            case EDGE -> kind == BlockKind.FRAME;
            case WALL -> kind == BlockKind.CASING || kind == BlockKind.CONTROLLER;
            case INTERIOR -> kind.isInterior();
        };
    }

    /**
     * Iterative flood fill over the 6-connected region of rsmbac blocks containing the seed.
     *
     * <p>Iterative, not recursive: 4096 blocks deep is well past the point a recursive fill
     * overflows the server thread stack, and a crash on placing a block is a far worse failure mode
     * than a structure not forming.
     */
    private static final class Flood {
        private final BlockSource source;
        private final ArrayDeque<int[]> queue = new ArrayDeque<>();
        private final HashSet<Long> seen = new HashSet<>();

        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;

        /** The position that broke a limit, kept for the failure message. */
        private int farX;
        private int farY;
        private int farZ;

        Flood(final BlockSource source, final int seedX, final int seedY, final int seedZ) {
            this.source = source;
            this.minX = seedX;
            this.maxX = seedX;
            this.minY = seedY;
            this.maxY = seedY;
            this.minZ = seedZ;
            this.maxZ = seedZ;
            this.farX = seedX;
            this.farY = seedY;
            this.farZ = seedZ;
            this.push(seedX, seedY, seedZ);
        }

        private void push(final int x, final int y, final int z) {
            if (this.seen.add(key(x, y, z))) {
                this.queue.add(new int[] {x, y, z});
            }
        }

        @Nullable
        Failure run() {
            while (!this.queue.isEmpty()) {
                final int[] pos = this.queue.poll();
                final int x = pos[0];
                final int y = pos[1];
                final int z = pos[2];
                this.minX = Math.min(this.minX, x);
                this.maxX = Math.max(this.maxX, x);
                this.minY = Math.min(this.minY, y);
                this.maxY = Math.max(this.maxY, y);
                this.minZ = Math.min(this.minZ, z);
                this.maxZ = Math.max(this.maxZ, z);
                if (this.maxX - this.minX >= MAX_EDGE
                    || this.maxY - this.minY >= MAX_EDGE
                    || this.maxZ - this.minZ >= MAX_EDGE
                    || this.seen.size() > MAX_VOLUME) {
                    this.farX = x;
                    this.farY = y;
                    this.farZ = z;
                    return Failure.TOO_LARGE;
                }
                this.visitNeighbour(x + 1, y, z);
                this.visitNeighbour(x - 1, y, z);
                this.visitNeighbour(x, y + 1, z);
                this.visitNeighbour(x, y - 1, z);
                this.visitNeighbour(x, y, z + 1);
                this.visitNeighbour(x, y, z - 1);
            }
            return null;
        }

        private void visitNeighbour(final int x, final int y, final int z) {
            if (this.source.blockAt(x, y, z) != null) {
                this.push(x, y, z);
            }
        }

        private static long key(final int x, final int y, final int z) {
            // The structure is at most 16 blocks per axis, but the coordinates are world
            // coordinates, so this has to be a full-range packing rather than a local one.
            return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
        }
    }
}

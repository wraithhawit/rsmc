package com.wraithhawit.rsmc.structure;

import java.util.ArrayDeque;
import java.util.HashSet;

import javax.annotation.Nullable;

/**
 * Finds and validates the crafter multiblock: a <em>solid</em> rectangular box of rsmc blocks,
 * between 1x1x1 and 16x16x16.
 *
 * <p>Deliberately free of Minecraft types. Structure detection is the one part of this mod that is
 * pure geometry, and keeping it that way means it can be exercised in a plain JVM by
 * {@code ./gradlew shapeCheck} instead of by launching the game and building boxes by hand. The
 * level is reached only through {@link BlockSource}.
 *
 * <h2>Why a solid box and not any connected blob</h2>
 *
 * <p>A shape rule has to answer two questions a player will ask: did my structure form, and if not,
 * which block is wrong. An arbitrary connected blob answers neither, because there is no such thing
 * as a missing block in a shape with no expected form. A filled box gives every failure a
 * coordinate, which is what {@link Result#failurePos} carries.
 *
 * <p><strong>Consequence worth knowing:</strong> two separate boxes placed flush against each other
 * are one connected region, and that region is not a box, so <em>both</em> stop working. Leave a
 * gap. It is reported as {@link Failure#NOT_SOLID} against a position in the hole.
 */
public final class MultiblockShape {
    /** Per axis, matching the design: a 16x16x16 box of 4096 blocks is the largest legal one. */
    public static final int MAX_EDGE = 16;

    /**
     * The most blocks a search will visit before giving up -- the largest legal structure. Without
     * a cap, a player who floors an entire chunk in crafter blocks would have this scanning
     * hundreds of thousands of positions on the tick the last one is placed.
     */
    public static final int MAX_VOLUME = MAX_EDGE * MAX_EDGE * MAX_EDGE;

    private MultiblockShape() {
    }

    /** What the level looks like to the search. */
    @FunctionalInterface
    public interface BlockSource {
        /**
         * @return the rsmc block at this position, or null for anything else -- air, stone, a
         *     chest, or an unloaded chunk. Treating unloaded as "not ours" is deliberate: a
         *     structure must never form across a chunk boundary it cannot see, or it would
         *     silently change shape when that chunk loads.
         */
        @Nullable
        Component blockAt(int x, int y, int z);
    }

    /**
     * One block of the structure.
     *
     * @param kind which of the two block types it is
     * @param tier the CPU tier, or null for a pattern storage -- there is only one storage tier
     */
    public record Component(BlockKind kind, @Nullable CpuTier tier) {
        public static Component cpu(final CpuTier tier) {
            return new Component(BlockKind.CPU, tier);
        }

        public static Component patternStorage() {
            return new Component(BlockKind.PATTERN_STORAGE, null);
        }
    }

    /** The two block types the structure is built from. */
    public enum BlockKind {
        /** Adds crafting throughput. Tiered. */
        CPU,
        /** Holds patterns. One tier only. */
        PATTERN_STORAGE
    }

    /** Why a structure did not form. */
    public enum Failure {
        /**
         * The connected region is not a filled box: its bounding box contains a position that is
         * not an rsmc block. {@link Result#failurePos} is that hole.
         */
        NOT_SOLID,
        /**
         * The region runs past {@link #MAX_EDGE} along at least one axis. {@link Result#failurePos}
         * is the block that overshot.
         */
        TOO_LARGE
    }

    /**
     * A formed structure, or the reason there is not one.
     *
     * @param formed          whether the bounds and counts mean anything
     * @param failure         null when formed
     * @param failurePos      the offending position as {x, y, z}, or null when formed
     * @param cpus            how many CPU blocks the box contains, of any tier
     * @param stepsPerTick    the sum of those CPUs' tier weights -- the structure's crafting rate
     * @param patternStorages how many pattern storage blocks the box contains
     */
    public record Result(boolean formed,
                         @Nullable Failure failure,
                         @Nullable int[] failurePos,
                         int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ,
                         int cpus,
                         int stepsPerTick,
                         int patternStorages) {
        public int volume() {
            return (this.maxX - this.minX + 1)
                * (this.maxY - this.minY + 1)
                * (this.maxZ - this.minZ + 1);
        }

        static Result failed(final Failure failure, final int x, final int y, final int z) {
            return new Result(false, failure, new int[] {x, y, z}, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Walks the region containing the seed and decides whether it is a legal structure.
     *
     * <p>Two passes rather than one clever pass. The first floods the connected region to find its
     * extent; the second checks every position inside the resulting bounding box. One pass cannot
     * do this: "is the box solid" is not a property any individual block has, and a flood fill that
     * has visited N blocks knows nothing about whether the box it spans is full until it knows what
     * that box is.
     */
    public static Result find(final BlockSource source,
                              final int seedX, final int seedY, final int seedZ) {
        if (source.blockAt(seedX, seedY, seedZ) == null) {
            return Result.failed(Failure.NOT_SOLID, seedX, seedY, seedZ);
        }
        final Flood flood = new Flood(source, seedX, seedY, seedZ);
        final Failure floodFailure = flood.run();
        if (floodFailure != null) {
            return Result.failed(floodFailure, flood.farX, flood.farY, flood.farZ);
        }
        // The bounding box is known now, so it can be checked for holes. Counting the two block
        // kinds happens here rather than during the flood because the flood visits exactly the
        // same positions only when the region IS a box -- which is the thing not yet decided.
        int cpus = 0;
        int stepsPerTick = 0;
        int patternStorages = 0;
        for (int x = flood.minX; x <= flood.maxX; x++) {
            for (int y = flood.minY; y <= flood.maxY; y++) {
                for (int z = flood.minZ; z <= flood.maxZ; z++) {
                    final Component component = source.blockAt(x, y, z);
                    if (component == null) {
                        return Result.failed(Failure.NOT_SOLID, x, y, z);
                    }
                    if (component.kind() == BlockKind.CPU && component.tier() != null) {
                        cpus++;
                        stepsPerTick += component.tier().stepsPerTick();
                    } else {
                        patternStorages++;
                    }
                }
            }
        }
        return new Result(true, null, null,
            flood.minX, flood.minY, flood.minZ,
            flood.maxX, flood.maxY, flood.maxZ,
            cpus, stepsPerTick, patternStorages);
    }

    /**
     * Iterative flood fill over the 6-connected region of rsmc blocks containing the seed.
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

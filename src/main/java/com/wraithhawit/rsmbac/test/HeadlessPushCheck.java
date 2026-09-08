package com.wraithhawit.rsmbac.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternLayout;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.api.network.autocrafting.PatternListener;
import com.refinedmods.refinedstorage.api.network.impl.autocrafting.AutocraftingNetworkComponentImpl;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;

/**
 * Prices a pattern push, so the Controller's budget is set from a measurement rather than a guess.
 *
 * <h2>Why this can be headless at all</h2>
 *
 * <p>The whole push path -- {@code PatternProviderNetworkNode.setPattern}, the
 * {@code ParentContainer.add} it calls, and the {@code PatternRepositoryImpl} behind that -- lives in
 * Refined Storage's <b>api</b> module and contains <b>no {@code net.minecraft} references at all</b>.
 * {@code Pattern} is a record of a UUID and a layout, and {@code ResourceKey} is an empty marker
 * interface. So the real code can be driven in a plain JVM, at LavaSurf's scale and past it, in the
 * time it takes to boot nothing.
 *
 * <h2>What it answers</h2>
 *
 * <ul>
 *   <li><b>What one push costs</b>, which is the number {@code PATTERN_PUSHES_PER_REFRESH = 8} was
 *       chosen without.</li>
 *   <li><b>Whether that cost is flat.</b> Every estimate of "a structure this big takes this long"
 *       assumes linear. {@code PatternRepositoryImpl} keeps a {@code PriorityQueue} per output, so
 *       patterns sharing an output are the case where it might not be -- measured separately.</li>
 *   <li><b>Join-then-fill versus fill-then-join.</b> {@code setPattern} notifies {@code parents}
 *       twice per call and does nothing else, so before the node joins a network it is a bare array
 *       store. {@code onAddedIntoContainer} then walks the array itself, skipping nulls. If that is
 *       much cheaper, the load path should stop dripping and simply hand RS a full node.</li>
 *   <li><b>What the listeners cost</b>, since that is the term the existing budget blames and the
 *       only one that is not obviously O(1).</li>
 * </ul>
 *
 * <h2>What it cannot answer</h2>
 *
 * <p>The real {@link PatternListener}s are Minecraft-side -- grid and autocrafting monitor menus --
 * so the listener figure here is for <em>synthetic</em> listeners and prices the notification
 * mechanism, not what RS's grid does inside it. Headless sets the budget; one in-game check with a
 * grid open confirms it.
 */
public final class HeadlessPushCheck {
    /**
     * LavaSurf's structure, which is where the seven-hour report came from.
     *
     * <p>10x11x13 is an interior of 8x9x11 = 792 blocks. Taking about half as Pattern Storage at 54
     * slots each gives the capacity below, holding roughly the patterns he had moved in.
     */
    private static final int LAVASURF_CAPACITY = 400 * 54;
    private static final int LAVASURF_PATTERNS = 2800;

    /** A 16³ of nothing but Pattern Storage: the largest structure the shape rules allow. */
    private static final int MAX_CAPACITY = 2744 * 54;

    private HeadlessPushCheck() {
    }

    /**
     * Repetitions per measurement, of which the fastest is reported.
     *
     * <p>The minimum rather than the mean: every source of noise here -- a GC pause, the OS taking
     * the core away, a not-yet-compiled method -- makes a run <em>slower</em> and none makes it
     * faster, so the fastest run is the closest to the cost of the code itself.
     */
    private static final int REPEATS = 7;

    public static void main(final String[] args) {
        // Without this the first measurement prices the JIT rather than the push. The first run of
        // this file reported 14.6 us/push at 1,000 patterns falling to 0.75 us at 100,000, and
        // "more listeners is faster" -- both of which are warmup, and neither of which is a fact
        // about Refined Storage.
        warmUp();

        System.out.println("=== one push, by scale (join first, then push) ===");
        final double perPush = pushCostSweep();

        System.out.println();
        System.out.println("=== shared outputs, where the per-output PriorityQueue grows ===");
        sharedOutputSweep();

        System.out.println();
        System.out.println("=== listeners ===");
        listenerSweep();

        System.out.println();
        System.out.println("=== join-then-fill vs fill-then-join ===");
        compareJoinOrder(LAVASURF_CAPACITY, LAVASURF_PATTERNS, "LavaSurf");
        compareJoinOrder(MAX_CAPACITY, 20000, "maxed 16³");

        System.out.println();
        System.out.println("=== what that means for the budget ===");
        report(perPush);
    }

    /** Drives every path enough times for the JIT to have compiled it before anything is timed. */
    private static void warmUp() {
        final List<Pattern> distinct = distinctOutputPatterns(5000);
        final List<Pattern> shared = sharedOutputPatterns(5000);
        for (int i = 0; i < 20; i++) {
            timeJoinThenFill(5000, distinct, 0);
            timeJoinThenFill(5000, distinct, 4);
            timeJoinThenFill(5000, shared, 0);
            timeFillThenJoin(20000, distinct, 0);
        }
    }

    /** The fastest of {@link #REPEATS} runs; see that field for why the fastest. */
    private static long best(final java.util.function.LongSupplier run) {
        long bestNanos = Long.MAX_VALUE;
        for (int i = 0; i < REPEATS; i++) {
            bestNanos = Math.min(bestNanos, run.getAsLong());
        }
        return bestNanos;
    }

    /** Times a push at several sizes, to show whether the cost per pattern is flat. */
    private static double pushCostSweep() {
        double atScale = 0;
        for (final int n : new int[] {1000, 5000, 20000, 50000, 100000}) {
            final List<Pattern> patterns = distinctOutputPatterns(n);
            final long nanos = best(() -> timeJoinThenFill(Math.max(n, 64), patterns, 0));
            final double per = nanos / (double) n;
            if (n == 20000) {
                // Reported from the middle of the range rather than the end: the largest run is the
                // one most helped by a warm cache and least like a structure loading in a live game.
                atScale = per;
            }
            System.out.printf("  %,9d patterns  %8.2f ms total  %7.3f us/push%n",
                n, nanos / 1_000_000.0, per / 1000.0);
        }
        return atScale;
    }

    /**
     * The same, but every pattern makes the same thing.
     *
     * <p>{@code PatternRepositoryImpl} keeps a {@code PriorityQueue} keyed by output, so this is
     * where a per-push cost could stop being flat -- one queue holding every pattern instead of one
     * queue each.
     */
    private static void sharedOutputSweep() {
        for (final int n : new int[] {1000, 20000, 100000}) {
            final List<Pattern> patterns = sharedOutputPatterns(n);
            final long nanos = best(() -> timeJoinThenFill(Math.max(n, 64), patterns, 0));
            System.out.printf("  %,9d patterns  %8.2f ms total  %7.3f us/push%n",
                n, nanos / 1_000_000.0, nanos / (double) n / 1000.0);
        }
    }

    /** How much a notification costs, at the four listeners Refined Storage is known to keep. */
    private static void listenerSweep() {
        final List<Pattern> patterns = distinctOutputPatterns(20000);
        for (final int listeners : new int[] {0, 1, 4, 16}) {
            final long nanos = best(() -> timeJoinThenFill(20000, patterns, listeners));
            System.out.printf("  %2d listeners  %8.2f ms for %,d  %7.3f us/push%n",
                listeners, nanos / 1_000_000.0, patterns.size(),
                nanos / (double) patterns.size() / 1000.0);
        }
    }

    /**
     * The two ways to get a full node onto a network.
     *
     * <p>A is what the Controller does now. B fills the array while {@code parents} is empty -- where
     * {@code setPattern} is a bare array store -- and then joins once, letting
     * {@code onAddedIntoContainer} do the registration in its own pass.
     */
    private static void compareJoinOrder(final int capacity, final int count, final String label) {
        final List<Pattern> patterns = distinctOutputPatterns(count);
        final long a = best(() -> timeJoinThenFill(capacity, patterns, 0));
        final long b = best(() -> timeFillThenJoin(capacity, patterns, 0));
        System.out.printf("  %-12s %,7d patterns in %,7d slots%n", label, count, capacity);
        System.out.printf("      A join-then-fill  %8.2f ms%n", a / 1_000_000.0);
        System.out.printf("      B fill-then-join  %8.2f ms   (%.2fx)%n",
            b / 1_000_000.0, a / (double) b);
    }

    /** Today's order: the node joins the network, then patterns are pushed in one at a time. */
    private static long timeJoinThenFill(final int capacity, final List<Pattern> patterns,
                                         final int listeners) {
        final AutocraftingNetworkComponentImpl component = component(listeners);
        final PatternProviderNetworkNode node = new PatternProviderNetworkNode(0L, capacity);
        node.onAddedIntoContainer(component);
        final long start = System.nanoTime();
        for (int i = 0; i < patterns.size(); i++) {
            node.setPattern(i, patterns.get(i));
        }
        return System.nanoTime() - start;
    }

    /** The alternative: fill the array while nothing is listening, then join once. */
    private static long timeFillThenJoin(final int capacity, final List<Pattern> patterns,
                                         final int listeners) {
        final AutocraftingNetworkComponentImpl component = component(listeners);
        final PatternProviderNetworkNode node = new PatternProviderNetworkNode(0L, capacity);
        final long start = System.nanoTime();
        for (int i = 0; i < patterns.size(); i++) {
            node.setPattern(i, patterns.get(i));
        }
        node.onAddedIntoContainer(component);
        return System.nanoTime() - start;
    }

    private static AutocraftingNetworkComponentImpl component(final int listeners) {
        // add() never touches the root storage, so a supplier of null is enough to build one.
        // Daemon threads, and one per component: this is built thousands of times across the
        // repeats, and a pool of non-daemon threads would both pile up and stop the JVM exiting.
        final AutocraftingNetworkComponentImpl component = new AutocraftingNetworkComponentImpl(
            () -> null,
            Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "rsmbac-pushcheck");
                thread.setDaemon(true);
                return thread;
            }));
        for (int i = 0; i < listeners; i++) {
            component.addListener(new CountingListener());
        }
        return component;
    }

    /** Each pattern makes something different, which is the ordinary case. */
    private static List<Pattern> distinctOutputPatterns(final int count) {
        final List<Pattern> patterns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            patterns.add(pattern(new Key(i), new Key(count + i)));
        }
        return patterns;
    }

    /** Every pattern makes the same thing: the worst case for the per-output queue. */
    private static List<Pattern> sharedOutputPatterns(final int count) {
        final Key output = new Key(-1);
        final List<Pattern> patterns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            patterns.add(pattern(output, new Key(i)));
        }
        return patterns;
    }

    /** One output and nine inputs, which is the shape of a full crafting grid. */
    private static Pattern pattern(final ResourceKey output, final ResourceKey input) {
        final List<Ingredient> ingredients = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ingredients.add(new Ingredient(1, List.of(input)));
        }
        return new Pattern(UUID.randomUUID(), new PatternLayout(
            ingredients, List.of(new ResourceAmount(output, 1)), List.of(), PatternType.INTERNAL));
    }

    private static void report(final double nanosPerPush) {
        final double perTickBudgetMs = 2.0;
        final int pushesPerTick = (int) (perTickBudgetMs * 1_000_000.0 / nanosPerPush);
        System.out.printf("  measured           %.3f us per push%n", nanosPerPush / 1000.0);
        System.out.printf("  today              8 per refresh, 1 refresh/sec = 8/sec%n");
        System.out.printf("  LavaSurf's %,d patterns at that rate: %.1f minutes%n",
            LAVASURF_PATTERNS, LAVASURF_PATTERNS / 8.0 / 60.0);
        System.out.printf("  a %.0fms/tick budget would allow ~%,d pushes/tick = %,d/sec%n",
            perTickBudgetMs, pushesPerTick, pushesPerTick * 20);
        System.out.printf("  which drains %,d patterns in %.2f seconds%n",
            LAVASURF_PATTERNS, LAVASURF_PATTERNS / (double) (pushesPerTick * 20));
        System.out.printf("  and a maxed %,d-slot structure holding 20,000 in %.2f seconds%n",
            MAX_CAPACITY, 20000 / (double) (pushesPerTick * 20));
    }

    /** A distinct resource per id, with identity equality, which is all the repository needs. */
    private record Key(int id) implements ResourceKey {
    }

    /** Stands in for a grid or monitor menu: counts, so the call cannot be optimised away. */
    private static final class CountingListener implements PatternListener {
        private int added;

        @Override
        public void onAdded(final Pattern pattern) {
            this.added++;
        }

        @Override
        public void onRemoved(final Pattern pattern) {
            this.added--;
        }
    }
}

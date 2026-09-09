package com.wraithhawit.rsmbac;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.autocrafting.PatternInventory;

import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.PatternStorageBlockEntity;
import com.wraithhawit.rsmbac.menu.StructurePatterns;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Moves patterns out of the autocrafters on a network and into a multiblock structure.
 *
 * <h2>Why this has to be a command rather than a pipe</h2>
 *
 * <p><b>No autocrafter exposes its patterns to item transport.</b> Refined Storage registers
 * {@code Capabilities.ItemHandler.BLOCK} for its Disk Drive and its Interface and for nothing else;
 * Cable Tiers registers one for its Disk Interfaces and Interfaces, and gives its tiered
 * autocrafters only the network-node capability. So there is nothing for a hopper, a pipe or Super
 * Factory Manager to pull from -- patterns were always meant to move through the Autocrafter
 * Manager screen by hand. Reported from a survival world as "SFM can't pull from mega autocrafters,
 * and if SFM can't I don't think any mod can", which is exactly right.
 *
 * <p>Since nothing outside can reach in, the move has to be made from inside. That turns out to be
 * the better tool anyway: no transport mod sits in the middle to void a stack, nothing becomes an
 * item entity, and the operation can say what it did.
 *
 * <h2>Duplication is the safe failure, deletion is not</h2>
 *
 * <p>Every pattern is <b>written to the destination and read back before it is removed from the
 * source</b>. If anything goes wrong between those two steps the pattern exists twice, which a
 * player can see and fix; the other order loses it silently. This is a migration tool pointed at
 * someone's entire base, so the ordering is chosen for what happens when it fails, not for what
 * happens when it works.
 *
 * <h2>Reaching a foreign mod's container</h2>
 *
 * <p>Both {@code AutocrafterBlockEntity} and Cable Tiers' {@code TieredAutocrafterBlockEntity} hold
 * a private {@link PatternInventory} -- the latter having copied the former -- and neither exposes
 * it publicly. There is no interface to ask, so the field is found by type rather than by name: any
 * block entity on the network with a field assignable to {@code PatternInventory} is treated as an
 * autocrafter. That works for Refined Storage, for Cable Tiers, and for any other addon that builds
 * on the same class, without a compile-time dependency on any of them -- which matters, because
 * Cable Tiers is a mod this one already has to work around elsewhere.
 */
public final class PatternImport {
    /** Cached per class, since a network can hold hundreds of autocrafters of a few types. */
    private static final Map<Class<?>, Optional<Field>> PATTERN_FIELDS = new ConcurrentHashMap<>();

    private PatternImport() {
    }

    /** What an import did, or would do. */
    public record Report(int moved, int sources, int freeSlotsLeft, int leftBehind,
                         boolean destinationFull, @Nullable String failure) {
        public boolean failed() {
            return this.failure != null;
        }
    }

    /**
     * Moves every pattern reachable on the structure's network into the structure.
     *
     * @param dryRun when true, counts what would move and changes nothing
     */
    public static Report run(final Level level, final BlockPos structureSeed, final Result result,
                             final boolean dryRun) {
        if (!result.formed()) {
            return failure("The structure is not formed.");
        }
        final int[] controller = result.controllerPos();
        if (controller == null) {
            return failure("The structure has no Controller.");
        }
        final BlockPos controllerPos = new BlockPos(controller[0], controller[1], controller[2]);
        if (!(level.getBlockEntity(controllerPos) instanceof ControllerBlockEntity controllerEntity)) {
            return failure("No Controller block entity at " + controllerPos + ".");
        }
        final Network network = controllerEntity.node().getNetwork();
        if (network == null) {
            return failure("The structure is not connected to a network. Run a cable to it first.");
        }
        final StructurePatterns destination = StructurePatterns.of(level, result);
        if (destination.getContainerSize() == 0) {
            return failure("The structure has no Pattern Storage blocks.");
        }

        final List<PatternInventory> sources = findSources(level, network);
        int moved = 0;
        boolean full = false;
        int leftBehind = 0;
        for (final PatternInventory source : sources) {
            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                final ItemStack pattern = source.getItem(slot);
                if (pattern.isEmpty()) {
                    continue;
                }
                if (full) {
                    leftBehind++;
                    continue;
                }
                final int free = destination.firstFreeSlot();
                if (free < 0) {
                    full = true;
                    leftBehind++;
                    continue;
                }
                if (dryRun) {
                    moved++;
                    continue;
                }
                // Write first, read back, and only then take it from the source. See the class note:
                // a failure here has to leave the pattern somewhere, and two somewheres beats none.
                destination.setItem(free, pattern.copy());
                if (destination.getItem(free).isEmpty()) {
                    RSMBAC.LOGGER.error("[rsmbac] import could not write into slot {}; stopping with"
                        + " {} moved so far, nothing lost", free, moved);
                    return new Report(moved, sources.size(), countFree(destination),
                        leftBehind, false,
                        "The structure refused a pattern. Stopped after " + moved
                            + "; nothing was lost.");
                }
                // removeItem, not setItem(EMPTY): only the former tells PatternInventory's listener,
                // which is how the source autocrafter learns to drop the pattern from its own node.
                // Skip it and the old crafter goes on advertising a recipe it no longer holds.
                source.removeItem(slot, 1);
                moved++;
            }
        }
        return new Report(moved, sources.size(), countFree(destination), leftBehind, full, null);
    }

    /** How much room the structure has left, for the report. */
    private static int countFree(final StructurePatterns destination) {
        int free = 0;
        for (int slot = 0; slot < destination.getContainerSize(); slot++) {
            if (destination.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * Every pattern inventory on the network that is not part of this structure.
     *
     * <p>Walks the network's own container graph rather than the world, so it finds exactly what
     * the player would call "on this network" and nothing else -- an autocrafter ten thousand
     * blocks away in another dimension is included if it is wired in, and one sitting unconnected
     * next to the structure is not.
     */
    private static List<PatternInventory> findSources(final Level level, final Network network) {
        final List<PatternInventory> sources = new ArrayList<>();
        final GraphNetworkComponent graph = network.getComponent(GraphNetworkComponent.class);
        if (graph == null) {
            return sources;
        }
        final Set<BlockPos> seen = new HashSet<>();
        for (final NetworkNodeContainer container : graph.getContainers()) {
            if (!(container instanceof InWorldNetworkNodeContainer inWorld)) {
                continue;
            }
            final BlockPos pos = inWorld.getLocalPosition();
            // A block entity can back more than one container; its patterns must be taken once.
            if (!seen.add(pos)) {
                continue;
            }
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            // Never a Pattern Storage of ours. They hold a PatternInventory too, so without this
            // the import would happily "move" a structure's patterns into itself -- reshuffling a
            // player's slots for no reason, and doing it to whichever structure is on the network.
            if (blockEntity == null || blockEntity instanceof PatternStorageBlockEntity) {
                continue;
            }
            final PatternInventory inventory = patternInventoryOf(blockEntity);
            if (inventory != null && !inventory.isEmpty()) {
                sources.add(inventory);
            }
        }
        return sources;
    }

    /** The block entity's pattern inventory, found by field type, or null if it has none. */
    @Nullable
    private static PatternInventory patternInventoryOf(final BlockEntity blockEntity) {
        final Optional<Field> field = PATTERN_FIELDS.computeIfAbsent(
            blockEntity.getClass(), PatternImport::findPatternField);
        if (field.isEmpty()) {
            return null;
        }
        try {
            return (PatternInventory) field.get().get(blockEntity);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            // Never fatal: a block entity we cannot read is one we skip, and the import reports a
            // smaller number rather than failing outright.
            RSMBAC.LOGGER.warn("[rsmbac] could not read patterns from {}",
                blockEntity.getClass().getName(), e);
            return null;
        }
    }

    private static Optional<Field> findPatternField(final Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (final Field field : current.getDeclaredFields()) {
                if (!PatternInventory.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (final RuntimeException e) {
                    RSMBAC.LOGGER.warn("[rsmbac] {} holds patterns but they cannot be reached",
                        current.getName(), e);
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Report failure(final String message) {
        return new Report(0, 0, 0, 0, false, message);
    }
}

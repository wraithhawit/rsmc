package com.wraithhawit.rsmbac.menu;

import com.wraithhawit.rsmbac.block.ControllerBlock;
import com.wraithhawit.rsmbac.network.HighlightBlockPayload;
import com.wraithhawit.rsmbac.network.RsmcPayloads;
import com.wraithhawit.rsmbac.structure.LevelBlockSource;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Opens the pattern screen from any block in the structure.
 *
 * <p>Frame, Casing, Controller, CPU and Pattern Storage all behave identically: the whole box is one
 * machine, and asking a player to remember which block is the "real" one is friction with no upside.
 * Reborn Storage does the same, and it is the behaviour Wraith asked for.
 *
 * <p>Shared from one place rather than repeated on five blocks -- five copies of an interaction is
 * five chances for one of them to drift.
 *
 * <h2>A block that is not part of a structure says so</h2>
 *
 * <p>It does not open an empty window. An empty window is the least informative thing this could do:
 * it looks like a bug, and it hides the actual answer, which is that the box is not finished and
 * exactly which block is wrong. So the failure is reported in chat, in the same words
 * {@code /rsmbac info} uses, from the same {@link MultiblockShape#find} call.
 */
public final class PatternScreenOpener {
    private PatternScreenOpener() {
    }

    public static InteractionResult open(final Level level, final BlockPos pos, final Player player) {
        if (level.isClientSide()) {
            // Let the client assume it worked; the server decides and either opens a screen or
            // sends the explanation. Returning CONSUME here keeps the arm from swinging.
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        final Result result = MultiblockShape.find(
            new LevelBlockSource(level), pos.getX(), pos.getY(), pos.getZ());
        if (!result.formed()) {
            explain(serverPlayer, result,
                level.getBlockState(pos).getBlock() instanceof ControllerBlock);
            // PASS, not CONSUME. Consuming the interaction eats the block placement that came
            // with it, so building the box against a block that is already part of it silently
            // did nothing -- exactly when a player is placing the most blocks. An unformed
            // structure has no screen to open and nothing to protect, so the interaction should
            // carry on to whatever the held item wanted to do.
            return InteractionResult.PASS;
        }
        final StructurePatterns patterns = StructurePatterns.of(level, pos);
        if (patterns.getContainerSize() > MAX_SCREEN_SLOTS) {
            refuseTooManySlots(serverPlayer, patterns.getContainerSize());
            return InteractionResult.CONSUME;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
            (containerId, inventory, menuPlayer) ->
                new PatternMenu(containerId, inventory, patterns),
            Component.translatable("container.rsmbac.patterns")),
            buf -> buf.writeVarInt(patterns.getContainerSize()));
        return InteractionResult.CONSUME;
    }

    /**
     * The largest structure whose patterns can be shown in one window.
     *
     * <h2>This is Minecraft's limit, not Refined Storage's, and it binds the SCREEN only</h2>
     *
     * <p>A container slot index is a {@code short} on the wire in both directions --
     * {@code ClientboundContainerSetSlotPacket} writes one, {@code ServerboundContainerClickPacket}
     * reads one -- so a menu with more than 32,767 slots hands out indices that arrive negative and
     * a click lands on the wrong pattern. Nothing in RS imposes this: its
     * {@code PatternProviderNetworkNode} is a plain {@code Pattern[]} sized at construction with no
     * bound, and there is no such constant anywhere in its autocrafting package. RS's own Autocrafter
     * Manager builds one slot per pattern exactly as we do and never trips it, because an Autocrafter
     * holds nine patterns and nobody owns 3,641 of them. We hold 54 per block and reach it at 607.
     *
     * <p><strong>So the limit is not applied to the structure's capacity.</strong> Clamping that
     * would cripple crafting to fix a window: the node is happy at any size and a big structure
     * crafts perfectly well: it is only the act of looking at the patterns that cannot be expressed.
     * Capping capacity would also break the push path outright, because
     * {@code pushPatternsIfChanged} refuses to write while the view and the node disagree on size --
     * a clamp on one of them and nothing is ever pushed again.
     *
     * <p>Temporary. The windowed menu removes the reason for it, at which point this goes.
     */
    public static final int MAX_SCREEN_SLOTS = Short.MAX_VALUE;

    /**
     * Says why the window will not open, rather than opening a broken one.
     *
     * <p>The alternative is worse than it sounds: the menu would open, the content packet for tens
     * of thousands of patterns would exceed Minecraft's 8 MiB packet ceiling, and the player would
     * be disconnected -- which reads as the world corrupting rather than as a limit being hit.
     */
    private static void refuseTooManySlots(final ServerPlayer player, final int slots) {
        player.displayClientMessage(
            Component.literal("This crafter holds " + slots + " pattern slots, more than the "
                + MAX_SCREEN_SLOTS + " one screen can show.").withStyle(ChatFormatting.RED),
            false);
        player.displayClientMessage(
            Component.literal("  It still crafts. Remove some Pattern Storage blocks to open the"
                + " pattern screen again.").withStyle(ChatFormatting.GRAY),
            false);
    }

    private static void explain(final ServerPlayer player, final Result result,
                                final boolean fromController) {
        // Now that the interaction PASSes, one right-click can both place a block and report the
        // failure -- and placing a wall by hand is a right-click per block. A player asked for
        // this message on demand, not as a running commentary, so it speaks at most once a
        // second per player. The highlight is not rate-limited: it is deliberate, aimed, and
        // replaces itself rather than accumulating.
        final long now = player.level().getGameTime();
        final Long last = LAST_MESSAGE.get(player.getUUID());
        final boolean quiet = last != null && now - last < MESSAGE_INTERVAL_TICKS;
        LAST_MESSAGE.put(player.getUUID(), now);
        if (quiet && !fromController) {
            return;
        }
        player.displayClientMessage(
            Component.literal("Not formed: " + describe(result)).withStyle(ChatFormatting.RED),
            false);
        final int[] pos = result.failurePos();
        if (pos != null) {
            player.displayClientMessage(
                Component.literal("  at " + pos[0] + ", " + pos[1] + ", " + pos[2]
                    + (fromController ? "" : "  (right-click the Controller to highlight it)"))
                    .withStyle(ChatFormatting.GRAY),
                false);
        }
        // The failure most likely to be reported as a bug. Two structures built flush are one
        // connected region, that region is not a box, so BOTH stop working -- and nothing about
        // "there is a gap in the box" hints at that. The rule is deliberate (see MultiblockShape),
        // so the only thing that makes it survivable is saying it out loud at the moment it bites.
        if (result.failure() == MultiblockShape.Failure.NOT_SOLID
            || result.failure() == MultiblockShape.Failure.TOO_MANY_CONTROLLERS) {
            player.displayClientMessage(
                Component.literal("  If two crafters are touching, they count as one shape. "
                    + "Leave a gap between them.").withStyle(ChatFormatting.DARK_GRAY),
                false);
        }
        if (fromController && pos != null) {
            RsmcPayloads.highlight(player,
                new HighlightBlockPayload(new BlockPos(pos[0], pos[1], pos[2]), HIGHLIGHT_TICKS));
        }
    }

    /** Long enough to walk round the box and look, short enough not to become scenery. */
    private static final int HIGHLIGHT_TICKS = 20 * 15;

    /** How often one player can be told, at most. */
    private static final int MESSAGE_INTERVAL_TICKS = 20;

    /**
     * Last time each player was told, by uuid.
     *
     * <p>Never cleaned: an entry is two longs, and a server that has seen enough distinct players
     * for this to matter has larger maps than this one everywhere else.
     */
    private static final java.util.Map<java.util.UUID, Long> LAST_MESSAGE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** What a position needed to be, in the words the blocks are actually called. */
    private static String needed(@Nullable final MultiblockShape.Role role) {
        if (role == null) {
            return "a different block";
        }
        return switch (role) {
            case EDGE -> "a Frame";
            case WALL -> "a Casing, or the Controller";
            case INTERIOR -> "a Crafting CPU or a Pattern Storage";
        };
    }

    private static String describe(final Result result) {
        if (result.failure() == null) {
            return "unknown";
        }
        return switch (result.failure()) {
            case NOT_SOLID -> "there is a gap in the box -- that position needs "
                + needed(result.expected());
            case TOO_LARGE -> "bigger than " + MultiblockShape.MAX_EDGE + " blocks on some axis";
            case WRONG_BLOCK -> "wrong block for that position -- it needs "
                + needed(result.expected());
            case NO_CPU -> "no Crafting CPU inside";
            case NO_PATTERN_STORAGE -> "no Pattern Storage inside";
            case NO_CONTROLLER -> "no Controller -- swap one wall Casing for one";
            case TOO_MANY_CONTROLLERS -> "more than one Controller; remove this one";
        };
    }
}

package com.wraithhawit.rsmc.menu;

import com.wraithhawit.rsmc.block.ControllerBlock;
import com.wraithhawit.rsmc.network.HighlightBlockPayload;
import com.wraithhawit.rsmc.network.RsmcPayloads;
import com.wraithhawit.rsmc.structure.LevelBlockSource;
import com.wraithhawit.rsmc.structure.MultiblockShape;
import com.wraithhawit.rsmc.structure.MultiblockShape.Result;

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
 * {@code /rsmc info} uses, from the same {@link MultiblockShape#find} call.
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
            return InteractionResult.CONSUME;
        }
        final StructurePatterns patterns = StructurePatterns.of(level, pos);
        serverPlayer.openMenu(new SimpleMenuProvider(
            (containerId, inventory, menuPlayer) ->
                new PatternMenu(containerId, inventory, patterns),
            Component.translatable("container.rsmc.patterns")),
            buf -> buf.writeVarInt(patterns.getContainerSize()));
        return InteractionResult.CONSUME;
    }

    private static void explain(final ServerPlayer player, final Result result,
                                final boolean fromController) {
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

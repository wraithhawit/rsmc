package com.wraithhawit.rsmc.menu;

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
            explain(serverPlayer, result);
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

    private static void explain(final ServerPlayer player, final Result result) {
        player.displayClientMessage(
            Component.literal("No structure: " + describe(result)).withStyle(ChatFormatting.RED),
            false);
        final int[] pos = result.failurePos();
        if (pos != null) {
            player.displayClientMessage(
                Component.literal("  at " + pos[0] + ", " + pos[1] + ", " + pos[2])
                    .withStyle(ChatFormatting.GRAY),
                false);
        }
    }

    private static String describe(final Result result) {
        if (result.failure() == null) {
            return "unknown";
        }
        return switch (result.failure()) {
            case NOT_SOLID -> "there is a gap in the box";
            case TOO_LARGE -> "bigger than " + MultiblockShape.MAX_EDGE + " blocks on some axis";
            case WRONG_BLOCK -> "wrong block for that position";
            case NO_CPU -> "no Crafting CPU inside";
            case NO_PATTERN_STORAGE -> "no Pattern Storage inside";
            case NO_CONTROLLER -> "no Controller -- swap one wall Casing for one";
            case TOO_MANY_CONTROLLERS -> "more than one Controller; remove this one";
        };
    }
}

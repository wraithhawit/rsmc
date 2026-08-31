package com.wraithhawit.rsmbac;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.wraithhawit.rsmbac.block.ControllerBlock;
import com.wraithhawit.rsmbac.block.ControllerBlockEntity;
import com.wraithhawit.rsmbac.block.ControllerState;
import com.wraithhawit.rsmbac.menu.StructurePatterns;
import com.wraithhawit.rsmbac.structure.LevelBlockSource;
import com.wraithhawit.rsmbac.structure.MultiblockShape;
import com.wraithhawit.rsmbac.structure.MultiblockShape.Result;
import com.wraithhawit.rsmbac.structure.StructureBlock;
import com.wraithhawit.rsmbac.structure.StructurePower;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rsmbac info} -- explain the structure the player is looking at.
 *
 * <p>Exists because "I built it and nothing happened" has at least four causes that look identical
 * from inside the game: the box is wrong somewhere, the box is right but the interior is missing
 * something, the box is right and the feature simply is not written yet, or you are looking at a
 * different structure than you think. Without this, telling them apart means guessing.
 *
 * <p>Everything it prints comes from {@link MultiblockShape#find} -- the same call the mod itself
 * uses, not a reimplementation. A diagnostic that computes the answer a second way tells you about
 * itself rather than about the thing it is diagnosing.
 *
 * <p>Available to any player, no permission level: it reads the world and writes to one chat
 * window, and a tool that needs op to answer "why is my machine not working" is not a tool most
 * people will ever get to use.
 */
public final class StructureInfoCommand {
    /** How far to look for a block. Matches creative reach, which is the longest a player has. */
    private static final double REACH = 6.0;

    private StructureInfoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(RSMBAC.MODID)
            .then(Commands.literal("info").executes(context -> {
                info(context.getSource());
                return 1;
            }));
        event.getDispatcher().register(root);
    }

    private static void info(final CommandSourceStack source) {
        // First line, always, even on the failure paths. A result that cannot be tied to a build is
        // not evidence -- rstweaks learned this by mistaking a report from a three-version-old jar
        // for a confirmation.
        line(source, ChatFormatting.DARK_AQUA, "rsmbac v" + RSMBAC.version);
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player -- it reads what you look at."));
            return;
        }
        final HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block first."));
            return;
        }
        final BlockPos pos = blockHit.getBlockPos();
        if (!(player.level().getBlockState(pos).getBlock() instanceof StructureBlock)) {
            source.sendFailure(Component.literal(
                "That is not an rsmbac block. Look at a Frame, Casing, Controller, CPU or Pattern "
                    + "Storage."));
            return;
        }
        final Result result = MultiblockShape.find(
            new LevelBlockSource(player.level()), pos.getX(), pos.getY(), pos.getZ());
        reportScreen(source, player, pos, result);
        reportHeldItem(source, player, player.level(), pos, result);
        if (result.formed()) {
            reportFormed(source, player, result);
        } else {
            reportFailure(source, result);
        }
    }

    /**
     * What the server believes the Controller's screen says, next to what the structure actually is.
     *
     * <p>Added because of a report where a broken structure kept a lit screen. The two halves fail
     * differently and look identical from inside the game: if these two disagree, the server is
     * right and the client is drawing a stale model; if they agree and the screen still looks wrong,
     * the client did not get the update at all. Without printing both, telling those apart takes a
     * round trip per guess.
     */
    /**
     * What the structure says about the item in the player's hand.
     *
     * <p>Added because shift-clicking patterns in stopped working and reasoning about why did not
     * converge. Shift-click goes through {@code Container.canPlaceItem}, not through the slot, so
     * this asks the container exactly the question the transfer path asks -- with a real item, in a
     * real world, which is the part a test could not supply: an encoded pattern is not something
     * that can be conjured in a gametest.
     *
     * <p>It also separates the two ways this fails. RS's filter wants an <em>encoded</em> pattern,
     * so a blank one is refused and looks identical from the outside.
     */
    private static void reportHeldItem(final CommandSourceStack source, final ServerPlayer player,
                                       final Level level, final BlockPos pos, final Result result) {
        final ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !result.formed()) {
            return;
        }
        final StructurePatterns patterns = StructurePatterns.of(level, pos);
        if (patterns.getContainerSize() == 0) {
            line(source, ChatFormatting.RED, "  held item: no pattern slots to put it in");
            return;
        }
        final boolean accepted = patterns.canPlaceItem(0, held);
        final boolean isPatternItem = held.getItem() == BuiltInRegistries.ITEM
            .get(ResourceLocation.fromNamespaceAndPath("refinedstorage", "pattern"));
        final String detail;
        if (accepted) {
            detail = "accepted";
        } else if (isPatternItem) {
            detail = "REFUSED -- it is a pattern item, but not an encoded one";
        } else {
            detail = "refused -- not a pattern";
        }
        line(source, accepted ? ChatFormatting.GREEN : ChatFormatting.RED,
            "  held " + held.getHoverName().getString() + ": " + detail);
    }

    private static void reportScreen(final CommandSourceStack source, final ServerPlayer player,
                                     final BlockPos lookingAt, final Result result) {
        // The looked-at block first, because when the structure does NOT form there is no
        // controllerPos to fall back on -- and not forming is exactly the case this line exists
        // for. Looking at the Controller is what a player reporting a stuck screen would do.
        final int[] controller = result.controllerPos();
        final BlockPos controllerPos =
            player.level().getBlockState(lookingAt).getBlock() instanceof ControllerBlock
                ? lookingAt
                : controller == null ? null : new BlockPos(controller[0], controller[1], controller[2]);
        if (controllerPos == null) {
            return;
        }
        final BlockState state = player.level().getBlockState(controllerPos);
        if (!state.hasProperty(ControllerBlock.STATE)) {
            return;
        }
        final ControllerState screen = state.getValue(ControllerBlock.STATE);
        final boolean disagrees = !result.formed() && screen != ControllerState.UNFORMED;
        line(source, disagrees ? ChatFormatting.RED : ChatFormatting.DARK_GRAY,
            "  screen (server) " + screen.getSerializedName()
                + (disagrees ? "  <-- server disagrees with the structure; your client is stale"
                    : ""));
        reportNode(source, player, controllerPos, screen);
    }

    /**
     * What the node believes, printed beside what the screen says.
     *
     * <p>The screen line alone cannot close the case a player actually reports -- "it looks
     * unpowered but it is still crafting". Refined Storage's {@code PatternProviderNetworkNode
     * .doWork} returns immediately unless {@code isActive()}, so crafting <em>proves</em>
     * {@code active=true}; and the Controller sets that flag and the screen from the same
     * {@code hasEnergy()} call, a few statements apart. The two therefore cannot legitimately
     * differ, and printing both turns a theory into an observation:
     *
     * <ul>
     *   <li>screen {@code active} + node {@code active=true}, block still looks dark in game --
     *       both server halves are right and the <b>client</b> never got the update.
     *   <li>screen {@code inactive} + node {@code active=true} -- the server itself is
     *       inconsistent, and the block state is the half that is stale.
     * </ul>
     *
     * <p>Reads the node through {@link ControllerBlockEntity#diagnostics()} rather than
     * recomputing anything, for the reason this whole command exists.
     */
    private static void reportNode(final CommandSourceStack source, final ServerPlayer player,
                                   final BlockPos controllerPos, final ControllerState screen) {
        if (!(player.level().getBlockEntity(controllerPos)
            instanceof ControllerBlockEntity controller)) {
            return;
        }
        final ControllerBlockEntity.Diagnostics node = controller.diagnostics();
        final boolean nodeSaysRunning = node.nodeActive() && node.stepBehaviorActive();
        final boolean split = nodeSaysRunning != (screen == ControllerState.ACTIVE);
        line(source, split ? ChatFormatting.RED : ChatFormatting.DARK_GRAY,
            "  node (server)   active=" + node.nodeActive()
                + "  steps/tick=" + node.stepsPerTick()
                + (split ? "  <-- the node and the screen disagree; the screen is the stale half"
                    : ""));
        line(source, ChatFormatting.DARK_GRAY,
            "  energy (server) " + (node.energyRequired()
                ? (node.energyStored() < 0
                    ? "no network"
                    : node.energyStored() + " stored / " + node.energyUsage() + " needed")
                : "not required"));
    }

    /**
     * What the tick budget is currently allowing, and whether it is holding anything back.
     *
     * <p>Worth printing because a throttled structure and a slow one look identical from in front of
     * it. If crafting seems slower than the speed line says, this is the line that explains it — and
     * if it says the budget is not throttling, the slowness is somewhere else entirely.
     */
    private static void reportBudget(final CommandSourceStack source, final ServerPlayer player,
                                     @Nullable final int[] controller) {
        if (controller == null || Config.maxCraftingMillisPerTick <= 0) {
            line(source, ChatFormatting.DARK_GRAY,
                "  budget    off (maxCraftingMillisPerTick = 0)");
            return;
        }
        final BlockPos pos = new BlockPos(controller[0], controller[1], controller[2]);
        if (!(player.level().getBlockEntity(pos) instanceof ControllerBlockEntity entity)) {
            return;
        }
        final int rate = entity.stepBehavior().stepsPerTick();
        final int allowed = entity.currentAllowance();
        final boolean throttling = allowed < rate;
        line(source, throttling ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY,
            "  budget    " + Config.maxCraftingMillisPerTick + "ms/tick -> " + allowed
                + " of " + rate + " steps"
                + (throttling
                    ? "  (throttling to keep the tick short; raise maxCraftingMillisPerTick to"
                        + " favour crafting speed)"
                    : "  (not throttling)"));
    }

    private static void reportFormed(final CommandSourceStack source, final ServerPlayer player,
                                     final Result result) {
        line(source, ChatFormatting.GREEN, "Structure formed.");
        line(source, ChatFormatting.GRAY, "  size      "
            + result.sizeX() + " x " + result.sizeY() + " x " + result.sizeZ()
            + "  (" + result.volume() + " blocks)");
        line(source, ChatFormatting.GRAY, "  corner    "
            + result.minX() + ", " + result.minY() + ", " + result.minZ());
        final int[] controller = result.controllerPos();
        if (controller != null) {
            line(source, ChatFormatting.GRAY, "  controller "
                + controller[0] + ", " + controller[1] + ", " + controller[2]);
        }
        line(source, ChatFormatting.GRAY, "  CPUs      " + result.cpus()
            + "   pattern storage " + result.patternStorages());
        // How many patterns this structure puts into the network. Worth printing because it is the
        // number that makes RS's crafting calculator more expensive for the whole network: every
        // pattern is another branch the calculator may explore on any craft, not just ours.
        line(source, ChatFormatting.GRAY, "  patterns  " + patternsInUse(player, result)
            + " of " + (result.patternStorages() * StructurePower.PATTERNS_PER_STORAGE));
        line(source, ChatFormatting.AQUA, "  speed     " + result.stepsPerTick()
            + " steps/tick  (a fully upgraded RS autocrafter is 2.5)");
        line(source, ChatFormatting.GRAY,
            "  energy    " + StructurePower.energyUsage(result) + " FE/t to run");
        reportBudget(source, player, controller);
        // A formed structure that does nothing is the single most confusing state this mod can be
        // in, so the reason is named rather than left to be inferred from a screen colour. Until
        // 0.1.13 this said "the pattern provider is not implemented (issue #2)" -- true when it
        // was written and stale for ninety versions after that shipped, which is worse than
        // silence: it sent a player looking for a missing feature instead of a missing cable.
        final ControllerState state = controllerStateAt(player, controller);
        switch (state) {
            case ACTIVE -> line(source, ChatFormatting.GREEN,
                "  Running. Put patterns in and it will craft.");
            case INACTIVE -> line(source, ChatFormatting.YELLOW,
                "  Not running: no network, or not enough energy. Connect a cable to any face"
                    + " and give the network power.");
            case UNFORMED -> line(source, ChatFormatting.YELLOW,
                "  The Controller has not caught up with the structure yet; try again in a"
                    + " moment.");
        }
    }


    /**
     * What the Controller block itself currently believes, which is not always what
     * {@link MultiblockShape#find} just computed.
     *
     * <p>The two can disagree for a moment: the shape is derived here and now, while the
     * Controller updates on its refresh schedule and only joins the network on a later tick. That
     * gap is real and brief, and reporting it as UNFORMED with "try again in a moment" is more
     * honest than pretending the block agrees.
     */
    private static ControllerState controllerStateAt(final ServerPlayer player,
                                                     @Nullable final int[] controller) {
        if (controller == null) {
            return ControllerState.UNFORMED;
        }
        final BlockPos pos = new BlockPos(controller[0], controller[1], controller[2]);
        final BlockState state = player.level().getBlockState(pos);
        if (!state.hasProperty(ControllerBlock.STATE)) {
            return ControllerState.UNFORMED;
        }
        return state.getValue(ControllerBlock.STATE);
    }

    /** How many pattern slots in the structure actually hold something. */
    private static int patternsInUse(final ServerPlayer player, final Result result) {
        final StructurePatterns patterns = StructurePatterns.of(player.level(), result);
        int used = 0;
        for (int slot = 0; slot < patterns.getContainerSize(); slot++) {
            if (!patterns.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private static void reportFailure(final CommandSourceStack source, final Result result) {
        line(source, ChatFormatting.RED, "No structure: " + describe(result));
        final int[] pos = result.failurePos();
        if (pos != null) {
            line(source, ChatFormatting.GRAY,
                "  at " + pos[0] + ", " + pos[1] + ", " + pos[2]
                    + (result.expected() == null ? "" : "  (wants " + wanted(result) + ")"));
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

    private static String wanted(final Result result) {
        if (result.expected() == null) {
            return "";
        }
        return switch (result.expected()) {
            case EDGE -> "a Frame -- this is an edge or corner";
            case WALL -> "a Casing, or the one Controller";
            case INTERIOR -> "a CPU or Pattern Storage";
        };
    }

    private static void line(final CommandSourceStack source, final ChatFormatting colour,
                             final String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(colour), false);
    }
}

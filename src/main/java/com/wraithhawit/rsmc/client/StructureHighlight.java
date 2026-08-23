package com.wraithhawit.rsmc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wraithhawit.rsmc.network.ClientHighlightHandler;
import com.wraithhawit.rsmc.network.HighlightBlockPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws an outline around the block that is stopping a structure from forming.
 *
 * <p>Client-side and entirely transient: one position and a countdown, replaced whenever a newer
 * highlight arrives. Nothing here is saved, and losing it costs a right-click.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} and deliberately <b>without</b> depth testing, so
 * the outline is visible through the structure's own walls. A player asking "which block is
 * wrong" is usually standing outside a box whose offending position is on the far side; an
 * outline they cannot see through the machine answers nothing.
 */
public final class StructureHighlight {
    /** Slightly larger than the block, so the outline does not z-fight with its own faces. */
    private static final double INFLATE = 0.002;

    private static BlockPos target;
    private static int ticksLeft;

    private StructureHighlight() {
    }

    /** Installs this as the packet handler. Called from client setup only. */
    public static void install() {
        ClientHighlightHandler.HANDLER = StructureHighlight::accept;
    }

    private static void accept(final HighlightBlockPayload payload) {
        target = payload.pos();
        ticksLeft = payload.durationTicks();
    }

    /** Counts the highlight down. Bound to the client tick, not the render frame. */
    public static void tick() {
        if (ticksLeft > 0 && --ticksLeft == 0) {
            target = null;
        }
    }

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final BlockPos pos = target;
        if (pos == null || ticksLeft <= 0) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers =
            minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        // lines() rather than a depth-tested type: see the class comment.
        LevelRenderer.renderLineBox(
            poseStack,
            buffers.getBuffer(RenderType.lines()),
            new AABB(pos).inflate(INFLATE),
            1.0F, 0.25F, 0.25F, 1.0F);
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }
}

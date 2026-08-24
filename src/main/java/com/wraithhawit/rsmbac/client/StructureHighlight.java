package com.wraithhawit.rsmbac.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wraithhawit.rsmbac.network.ClientHighlightHandler;
import com.wraithhawit.rsmbac.network.HighlightBlockPayload;

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
 * <p>Drawn <b>without depth testing</b>, so the outline is visible through the structure's own
 * walls. That is the whole point: the offending block is very often <em>inside</em> the box, and
 * an outline hidden behind the machine answers nothing.
 *
 * <h2>Why the draw is done by hand</h2>
 *
 * <p>0.1.7 claimed this and did not do it. {@code RenderType.lines()} is depth-tested, and
 * handing it to a {@code MultiBufferSource} means the render type sets up its own GL state at
 * {@code endBatch} — so a {@code RenderSystem.disableDepthTest()} beforehand is simply overwritten
 * a moment later. The comment said "without depth testing" and the code did the opposite, which is
 * worse than either.
 *
 * <p>Building a no-depth {@code RenderType} needs {@code RenderType.create} and half of
 * {@code RenderStateShard}, all protected — a pile of access transformers for one outline. So
 * instead the lines state is set up, depth testing is disabled <em>after</em> that setup, and the
 * geometry is drawn immediately rather than queued. Nothing else shares the state, because
 * nothing else is batched in between.
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

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        final RenderType lines = RenderType.lines();
        lines.setupRenderState();
        // AFTER the render type's own setup, which would otherwise re-enable it.
        RenderSystem.disableDepthTest();

        final BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        LevelRenderer.renderLineBox(
            poseStack, builder, new AABB(pos).inflate(INFLATE), 1.0F, 0.25F, 0.25F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableDepthTest();
        lines.clearRenderState();
        poseStack.popPose();
    }
}

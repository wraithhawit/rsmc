package com.wraithhawit.rsmbac.network;

import com.wraithhawit.rsmbac.RSMBAC;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "Show the player which block is wrong."
 *
 * <p>Sent when someone right-clicks a Controller whose structure has not formed. The chat line
 * already names the coordinate, but a coordinate is a poor answer inside a 16³ box that a player
 * is standing in the middle of — reading three numbers and then finding that position by eye is
 * exactly the friction the message was meant to remove. This draws an outline around it instead.
 *
 * <p>Only the Controller does this, on Wraith's call. Every block in the structure explains the
 * failure in chat, but the Controller is the one block a player deliberately places to be the
 * machine's face, so it is the natural thing to ask "which one is wrong?".
 *
 * @param pos           the offending position
 * @param durationTicks how long the client should keep drawing it
 */
public record HighlightBlockPayload(BlockPos pos, int durationTicks) implements CustomPacketPayload {
    public static final Type<HighlightBlockPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RSMBAC.MODID, "highlight_block"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HighlightBlockPayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, HighlightBlockPayload::pos,
            ByteBufCodecs.VAR_INT, HighlightBlockPayload::durationTicks,
            HighlightBlockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

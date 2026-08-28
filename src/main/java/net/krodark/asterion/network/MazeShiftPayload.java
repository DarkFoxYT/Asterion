package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Localized rumble produced by one physical maze-wall shift. */
public record MazeShiftPayload(BlockPos center, float radius, float intensity,
                               int durationTicks) implements CustomPacketPayload {
    public static final Type<MazeShiftPayload> TYPE = new Type<>(Asterion.id("maze_shift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MazeShiftPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.center);
                buffer.writeFloat(payload.radius);
                buffer.writeFloat(payload.intensity);
                buffer.writeVarInt(payload.durationTicks);
            },
            buffer -> new MazeShiftPayload(buffer.readBlockPos(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

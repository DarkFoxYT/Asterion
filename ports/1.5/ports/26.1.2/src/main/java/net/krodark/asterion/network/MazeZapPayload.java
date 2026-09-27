package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record MazeZapPayload(int targetEntityId, Vec3 source, Vec3 impulse,
                             int durationTicks) implements CustomPacketPayload {
    public static final Type<MazeZapPayload> TYPE = new Type<>(Asterion.id("maze_zap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MazeZapPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.targetEntityId);
                buffer.writeDouble(payload.source.x);
                buffer.writeDouble(payload.source.y);
                buffer.writeDouble(payload.source.z);
                buffer.writeDouble(payload.impulse.x);
                buffer.writeDouble(payload.impulse.y);
                buffer.writeDouble(payload.impulse.z);
                buffer.writeVarInt(payload.durationTicks);
            },
            buffer -> new MazeZapPayload(buffer.readVarInt(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client telegraph for an imminent, small-area Dead Sun lightning strike. */
public record DeadSunStrikePayload(BlockPos target, int warningTicks, float radius,
                                   long seed) implements CustomPacketPayload {
    public static final Type<DeadSunStrikePayload> TYPE = new Type<>(Asterion.id("dead_sun_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeadSunStrikePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.target);
                buffer.writeVarInt(payload.warningTicks);
                buffer.writeFloat(payload.radius);
                buffer.writeLong(payload.seed);
            },
            buffer -> new DeadSunStrikePayload(buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readFloat(), buffer.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LimboSeaEventPayload(long tempestStart, long whirlpoolStart,
                                   int centerX, int centerZ) implements CustomPacketPayload {
    public static final Type<LimboSeaEventPayload> TYPE = new Type<>(Asterion.id("limbo_sea_events"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LimboSeaEventPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeLong(payload.tempestStart); buffer.writeLong(payload.whirlpoolStart);
                buffer.writeInt(payload.centerX); buffer.writeInt(payload.centerZ); },
            buffer -> new LimboSeaEventPayload(buffer.readLong(), buffer.readLong(),
                    buffer.readInt(), buffer.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

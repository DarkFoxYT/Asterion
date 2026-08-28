package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DazePayload(int durationTicks, int requiredPresses) implements CustomPacketPayload {
    public static final Type<DazePayload> TYPE = new Type<>(Asterion.id("daze"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DazePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DazePayload::durationTicks,
            ByteBufCodecs.VAR_INT, DazePayload::requiredPresses, DazePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

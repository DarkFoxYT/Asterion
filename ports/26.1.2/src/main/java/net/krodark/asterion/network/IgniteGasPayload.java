package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record IgniteGasPayload() implements CustomPacketPayload {
    public static final IgniteGasPayload INSTANCE = new IgniteGasPayload();
    public static final Type<IgniteGasPayload> TYPE = new Type<>(Asterion.id("ignite_gas"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IgniteGasPayload> CODEC = CustomPacketPayload.codec((p, b) -> {}, b -> INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

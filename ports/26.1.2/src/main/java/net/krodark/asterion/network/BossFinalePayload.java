package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BossFinalePayload() implements CustomPacketPayload {
    public static final BossFinalePayload INSTANCE = new BossFinalePayload();
    public static final Type<BossFinalePayload> TYPE = new Type<>(Asterion.id("boss_finale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossFinalePayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> { }, buffer -> INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

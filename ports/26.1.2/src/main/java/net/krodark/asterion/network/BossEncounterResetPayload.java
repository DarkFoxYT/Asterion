package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BossEncounterResetPayload() implements CustomPacketPayload {
    public static final BossEncounterResetPayload INSTANCE = new BossEncounterResetPayload();
    public static final Type<BossEncounterResetPayload> TYPE =
            new Type<>(Asterion.id("boss_encounter_reset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossEncounterResetPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> { }, buffer -> INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

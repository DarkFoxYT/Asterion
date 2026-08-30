package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Remaining time lets late arrivals join the existing shot without restarting the boss. */
public record BossEntrancePayload(Direction bossDoor, int elapsed, int duration) implements CustomPacketPayload {
    public static final Type<BossEntrancePayload> TYPE = new Type<>(Asterion.id("boss_entrance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossEntrancePayload> CODEC = StreamCodec.of(
            (out, p) -> { out.writeEnum(p.bossDoor); out.writeVarInt(p.elapsed); out.writeVarInt(p.duration); },
            in -> new BossEntrancePayload(in.readEnum(Direction.class), in.readVarInt(), in.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

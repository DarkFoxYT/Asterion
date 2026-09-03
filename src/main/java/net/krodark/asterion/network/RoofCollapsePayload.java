package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Starts the arena roof-failure camera sequence at the boss's collapse point. */
public record RoofCollapsePayload(Vec3 center, int duration) implements CustomPacketPayload {
    public static final Type<RoofCollapsePayload> TYPE = new Type<>(Asterion.id("roof_collapse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoofCollapsePayload> CODEC = StreamCodec.of(
            (out, value) -> {
                out.writeDouble(value.center.x); out.writeDouble(value.center.y); out.writeDouble(value.center.z);
                out.writeVarInt(value.duration);
            }, in -> new RoofCollapsePayload(new Vec3(in.readDouble(), in.readDouble(), in.readDouble()),
                    in.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

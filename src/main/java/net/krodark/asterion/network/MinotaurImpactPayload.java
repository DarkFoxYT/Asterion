package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** One wall impact; clients attenuate the shake from this world position. */
public record MinotaurImpactPayload(Vec3 position, float radius, float strength, int duration)
        implements CustomPacketPayload {
    public static final Type<MinotaurImpactPayload> TYPE = new Type<>(Asterion.id("minotaur_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinotaurImpactPayload> CODEC = StreamCodec.of(
            (out, value) -> {
                out.writeDouble(value.position.x); out.writeDouble(value.position.y); out.writeDouble(value.position.z);
                out.writeFloat(value.radius); out.writeFloat(value.strength); out.writeVarInt(value.duration);
            }, in -> new MinotaurImpactPayload(new Vec3(in.readDouble(), in.readDouble(), in.readDouble()),
                    in.readFloat(), in.readFloat(), in.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CentipedeMountPayload(int entityId, int seat, Vec3 point) implements CustomPacketPayload {
    public static final Type<CentipedeMountPayload> TYPE = new Type<>(Asterion.id("centipede_mount"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CentipedeMountPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeVarInt(payload.seat);
                buffer.writeDouble(payload.point.x);
                buffer.writeDouble(payload.point.y);
                buffer.writeDouble(payload.point.z);
            }, buffer -> new CentipedeMountPayload(buffer.readVarInt(), buffer.readVarInt(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Only orientation; ordinary Minecraft vehicle packets remain responsible for movement. */
public record CentipedeDriverFramePayload(int entityId, int surface, Vec3 forward) implements CustomPacketPayload {
    public static final Type<CentipedeDriverFramePayload> TYPE = new Type<>(Asterion.id("centipede_driver_frame"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CentipedeDriverFramePayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeVarInt(payload.surface);
                buffer.writeFloat((float)payload.forward.x);
                buffer.writeFloat((float)payload.forward.y);
                buffer.writeFloat((float)payload.forward.z);
            }, buffer -> new CentipedeDriverFramePayload(buffer.readVarInt(), buffer.readVarInt(),
                    new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

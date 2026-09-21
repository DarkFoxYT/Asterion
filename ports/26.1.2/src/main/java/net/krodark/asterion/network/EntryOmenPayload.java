package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EntryOmenPayload(net.minecraft.world.phys.Vec3 position) implements CustomPacketPayload {
    public static final Type<EntryOmenPayload> TYPE = new Type<>(Asterion.id("entry_omen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntryOmenPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> {
                buffer.writeDouble(payload.position.x);
                buffer.writeDouble(payload.position.y);
                buffer.writeDouble(payload.position.z);
            }, buffer -> new EntryOmenPayload(new net.minecraft.world.phys.Vec3(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

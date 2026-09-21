package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ObjectiveProgressPayload(int stage) implements CustomPacketPayload {
    public static final Type<ObjectiveProgressPayload> TYPE = new Type<>(Asterion.id("objective_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ObjectiveProgressPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> buffer.writeVarInt(payload.stage),
                    buffer -> new ObjectiveProgressPayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

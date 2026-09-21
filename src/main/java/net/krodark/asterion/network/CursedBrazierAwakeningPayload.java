package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CursedBrazierAwakeningPayload(int entityId, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<CursedBrazierAwakeningPayload> TYPE =
            new Type<>(Asterion.id("cursed_brazier_awakening"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CursedBrazierAwakeningPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.entityId);
                        buffer.writeVarInt(payload.durationTicks);
                    },
                    buffer -> new CursedBrazierAwakeningPayload(
                            buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

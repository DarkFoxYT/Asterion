package net.krodark.labyrinth.network;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Generic event envelope; new Dead Sun event types can reuse this packet unchanged. */
public record DeadSunEventPayload(Identifier eventId, long seed, int durationTicks, int elapsedTicks,
                                  float intensity) implements CustomPacketPayload {
    public static final Type<DeadSunEventPayload> TYPE = new Type<>(Labyrinth.id("dead_sun_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeadSunEventPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeIdentifier(payload.eventId);
                buffer.writeLong(payload.seed);
                buffer.writeVarInt(payload.durationTicks);
                buffer.writeVarInt(payload.elapsedTicks);
                buffer.writeFloat(payload.intensity);
            },
            buffer -> new DeadSunEventPayload(buffer.readIdentifier(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

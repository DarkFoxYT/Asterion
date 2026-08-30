package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BiomeAtmospherePayload(int biome) implements CustomPacketPayload {
    public static final Type<BiomeAtmospherePayload> TYPE =
            new Type<>(Asterion.id("biome_atmosphere"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BiomeAtmospherePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.biome),
            buffer -> new BiomeAtmospherePayload(buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

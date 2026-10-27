package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MinotaurGlobalSoundPayload(int sound, float volume, float pitch, long seed) implements CustomPacketPayload {
    public static final Type<MinotaurGlobalSoundPayload> TYPE = new Type<>(Asterion.id("minotaur_global_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinotaurGlobalSoundPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> {
                buffer.writeVarInt(payload.sound);
                buffer.writeFloat(payload.volume);
                buffer.writeFloat(payload.pitch);
                buffer.writeLong(payload.seed);
            }, buffer -> new MinotaurGlobalSoundPayload(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package net.fabricmc.fabric.api.networking.v1;

import net.krodark.asterion.port.forge.ForgeNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    PayloadTypeRegistry<RegistryFriendlyByteBuf> PLAY_C2S = new ForgeRegistry(false);
    PayloadTypeRegistry<RegistryFriendlyByteBuf> PLAY_S2C = new ForgeRegistry(true);

    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playC2S() { return PLAY_C2S; }
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C() { return PLAY_S2C; }

    final class ForgeRegistry implements PayloadTypeRegistry<RegistryFriendlyByteBuf> {
        private final boolean clientbound;
        private ForgeRegistry(boolean clientbound) { this.clientbound = clientbound; }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            if (clientbound) ForgeNetworking.registerClientbound(type, codec);
            else ForgeNetworking.registerServerbound(type, codec);
            return new CustomPacketPayload.TypeAndCodec(type, (StreamCodec) codec);
        }
    }
}

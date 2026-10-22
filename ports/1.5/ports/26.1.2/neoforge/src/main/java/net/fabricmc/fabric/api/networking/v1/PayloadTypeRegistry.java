package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PayloadTypeRegistry {
    private static final Registry CLIENTBOUND = new Registry(true),
            SERVERBOUND = new Registry(false);

    private PayloadTypeRegistry() {}

    public static Registry clientboundPlay() {
        return CLIENTBOUND;
    }

    public static Registry serverboundPlay() {
        return SERVERBOUND;
    }

    public static final class Registry {
        private final boolean client;

        private Registry(boolean client) {
            this.client = client;
        }

        public <T extends CustomPacketPayload> void register(
                CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            if (client) net.krodark.asterion.neoforge.NativeNetworking.clientbound(type, codec);
            else net.krodark.asterion.neoforge.NativeNetworking.serverbound(type, codec);
        }
    }
}

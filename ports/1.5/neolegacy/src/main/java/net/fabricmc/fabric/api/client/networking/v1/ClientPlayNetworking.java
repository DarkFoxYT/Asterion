package net.fabricmc.fabric.api.client.networking.v1;

import net.krodark.asterion.port.forge.nativelegacy.NativeLegacyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {}

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(
                net.minecraft.client.Minecraft client,
                net.minecraft.client.multiplayer.ClientPacketListener connection,
                FriendlyByteBuf input,
                Object sender);
    }

    public static boolean registerGlobalReceiver(ResourceLocation id, PlayChannelHandler handler) {
        return NativeLegacyNetwork.client(id, handler);
    }

    public static boolean canSend(ResourceLocation id) {
        return NativeLegacyNetwork.canSendToServer();
    }

    public static void send(ResourceLocation id, FriendlyByteBuf buffer) {
        NativeLegacyNetwork.sendToServer(id, buffer);
    }
}

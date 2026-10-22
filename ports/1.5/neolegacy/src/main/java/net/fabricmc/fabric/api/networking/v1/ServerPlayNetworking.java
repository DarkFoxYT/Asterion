package net.fabricmc.fabric.api.networking.v1;

import net.krodark.asterion.port.forge.nativelegacy.NativeLegacyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(
                net.minecraft.server.MinecraftServer server,
                ServerPlayer player,
                net.minecraft.server.network.ServerGamePacketListenerImpl connection,
                FriendlyByteBuf input,
                Object sender);
    }

    public static boolean registerGlobalReceiver(ResourceLocation id, PlayChannelHandler handler) {
        return NativeLegacyNetwork.server(id, handler);
    }

    public static boolean canSend(ServerPlayer player, ResourceLocation id) {
        return NativeLegacyNetwork.canSend(player);
    }

    public static void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
        NativeLegacyNetwork.send(player, id, buffer);
    }
}

package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    public record Context(ServerPlayer player) {
        public net.minecraft.server.MinecraftServer server() {
            return player.level().getServer();
        }
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return net.krodark.asterion.neoforge.NativeNetworking.serverHandler(type, handler);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }
}

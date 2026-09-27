package net.fabricmc.fabric.api.networking.v1;

import net.krodark.asterion.port.forge.ForgeNetworking;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return ForgeNetworking.registerServerHandler(type, handler);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ForgeNetworking.canSend(player);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetworking.send(player, payload);
    }

    public static PacketSender getSender(ServerPlayer player) {
        return new PacketSender() {
            @Override public Packet<?> createPacket(CustomPacketPayload payload) {
                throw new UnsupportedOperationException("Use sendPacket(payload) on Forge");
            }
            @Override public void sendPacket(Packet<?> packet, PacketSendListener listener) {
                player.connection.send(packet, listener);
            }
            @Override public void sendPacket(CustomPacketPayload payload) { ForgeNetworking.send(player, payload); }
            @Override public void disconnect(Component reason) { player.connection.disconnect(reason); }
        };
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> { void receive(T payload, Context context); }

    public interface Context {
        MinecraftServer server();
        ServerPlayer player();
        PacketSender responseSender();
    }
}

package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {}

    public record Context(Minecraft client) {
        public net.minecraft.client.player.LocalPlayer player() {
            return client.player;
        }
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return net.krodark.asterion.neoforge.NativeNetworking.clientHandler(type, handler);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        var c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(type);
    }

    public static void send(CustomPacketPayload payload) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
    }
}

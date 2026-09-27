package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.krodark.asterion.port.forge.ForgeNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return ForgeNetworking.registerClientHandler(type, handler);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return ForgeNetworking.canSendToServer();
    }

    public static void send(CustomPacketPayload payload) {
        ForgeNetworking.sendToServer(payload);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        Minecraft client();
        LocalPlayer player();
        PacketSender responseSender();
    }
}

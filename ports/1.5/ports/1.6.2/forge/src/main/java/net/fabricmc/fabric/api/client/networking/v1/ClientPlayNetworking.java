package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native Forge client play networking facade for shared packet handlers. */
public final class ClientPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> RECEIVERS = new ConcurrentHashMap<>();
    private ClientPlayNetworking() {}

    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public record Context(Minecraft client) {}

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type,
                                                                                PlayPayloadHandler<T> handler) {
        RECEIVERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public static void dispatch(CustomPacketPayload payload, CustomPayloadEvent.Context context) {
        PlayPayloadHandler<CustomPacketPayload> handler =
                (PlayPayloadHandler<CustomPacketPayload>) RECEIVERS.get(payload.type());
        if (handler != null) handler.receive(payload, new Context(Minecraft.getInstance()));
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null
                && PayloadTypeRegistry.channel().isRemotePresent(client.getConnection().getConnection());
    }

    public static void send(CustomPacketPayload payload) {
        PayloadTypeRegistry.channel().send(payload, PacketDistributor.SERVER.noArg());
    }
}

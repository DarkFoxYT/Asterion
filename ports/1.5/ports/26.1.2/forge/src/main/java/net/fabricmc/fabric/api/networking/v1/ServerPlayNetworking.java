package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native Forge server play networking facade for shared packet handlers. */
public final class ServerPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> RECEIVERS = new ConcurrentHashMap<>();
    private ServerPlayNetworking() {}

    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public record Context(ServerPlayer player) {
        public MinecraftServer server() { return player.level().getServer(); }
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type,
                                                                                PlayPayloadHandler<T> handler) {
        RECEIVERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public static void dispatch(CustomPacketPayload payload, CustomPayloadEvent.Context context) {
        PlayPayloadHandler<CustomPacketPayload> handler =
                (PlayPayloadHandler<CustomPacketPayload>) RECEIVERS.get(payload.type());
        if (handler != null && context.getSender() != null) handler.receive(payload, new Context(context.getSender()));
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return PayloadTypeRegistry.channel().isRemotePresent(player.connection.getConnection());
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PayloadTypeRegistry.channel().send(payload, PacketDistributor.PLAYER.with(player));
    }
}

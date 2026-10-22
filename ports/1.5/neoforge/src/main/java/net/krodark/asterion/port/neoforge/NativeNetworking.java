package net.krodark.asterion.port.neoforge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native negotiated play payloads. No global codec overrides or bridge mod. */
public final class NativeNetworking {
    private static final Map<
                    CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>>
            CLIENTBOUND = new LinkedHashMap<>();
    private static final Map<
                    CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>>
            SERVERBOUND = new LinkedHashMap<>();
    private static final Map<
                    CustomPacketPayload.Type<?>, ServerPlayNetworking.PlayPayloadHandler<?>>
            SERVER_HANDLERS = new LinkedHashMap<>();
    private static final Map<
                    CustomPacketPayload.Type<?>, ClientPlayNetworking.PlayPayloadHandler<?>>
            CLIENT_HANDLERS = new LinkedHashMap<>();
    private static boolean registered;

    private NativeNetworking() {}

    private static void requireOpen() {
        if (registered) throw new IllegalStateException("Asterion payload registration is closed");
    }

    public static <T extends CustomPacketPayload> void registerClientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        requireOpen();
        if (CLIENTBOUND.putIfAbsent(type, codec) != null)
            throw new IllegalStateException("Duplicate clientbound payload: " + type.id());
    }

    public static <T extends CustomPacketPayload> void registerServerbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        requireOpen();
        if (SERVERBOUND.putIfAbsent(type, codec) != null)
            throw new IllegalStateException("Duplicate serverbound payload: " + type.id());
    }

    public static <T extends CustomPacketPayload> boolean registerServerHandler(
            CustomPacketPayload.Type<T> type, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        return SERVER_HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static <T extends CustomPacketPayload> boolean registerClientHandler(
            CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        return CLIENT_HANDLERS.putIfAbsent(type, handler) == null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(RegisterPayloadHandlersEvent event) {
        requireOpen();
        var registrar = event.registrar("1.5.6");
        CLIENTBOUND.forEach(
                (type, codec) -> {
                    if (SERVERBOUND.containsKey(type)) {
                        registrar.playBidirectional(
                                (CustomPacketPayload.Type) type,
                                (StreamCodec) codec,
                                NativeNetworking::receive);
                    } else
                        registrar.playToClient(
                                (CustomPacketPayload.Type) type,
                                (StreamCodec) codec,
                                NativeNetworking::receive);
                });
        SERVERBOUND.forEach(
                (type, codec) -> {
                    if (!CLIENTBOUND.containsKey(type))
                        registrar.playToServer(
                                (CustomPacketPayload.Type) type,
                                (StreamCodec) codec,
                                NativeNetworking::receive);
                });
        registered = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void receive(CustomPacketPayload payload, IPayloadContext context) {
        // NeoForge's registrar dispatches these handlers on the game thread.
        if (context.player() instanceof ServerPlayer player) {
            var handler =
                    (ServerPlayNetworking.PlayPayloadHandler) SERVER_HANDLERS.get(payload.type());
            if (handler != null)
                handler.receive(
                        payload,
                        new ServerPlayNetworking.Context() {
                            public net.minecraft.server.MinecraftServer server() {
                                return player.getServer();
                            }

                            public ServerPlayer player() {
                                return player;
                            }

                            public net.fabricmc.fabric.api.networking.v1.PacketSender
                                    responseSender() {
                                return ServerPlayNetworking.getSender(player);
                            }
                        });
        } else ClientDispatch.receive(payload);
    }

    private static final class ClientDispatch {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void receive(CustomPacketPayload payload) {
            var handler =
                    (ClientPlayNetworking.PlayPayloadHandler) CLIENT_HANDLERS.get(payload.type());
            if (handler == null) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            handler.receive(
                    payload,
                    new ClientPlayNetworking.Context() {
                        public net.minecraft.client.Minecraft client() {
                            return client;
                        }

                        public net.minecraft.client.player.LocalPlayer player() {
                            return client.player;
                        }

                        public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
                            return new net.fabricmc.fabric.api.networking.v1.PacketSender() {
                                public net.minecraft.network.protocol.Packet<?> createPacket(
                                        CustomPacketPayload reply) {
                                    return new net.minecraft.network.protocol.common
                                            .ServerboundCustomPayloadPacket(reply);
                                }

                                public void sendPacket(
                                        net.minecraft.network.protocol.Packet<?> packet,
                                        net.minecraft.network.PacketSendListener listener) {
                                    client.getConnection().getConnection().send(packet, listener);
                                }

                                public void disconnect(
                                        net.minecraft.network.chat.Component reason) {
                                    client.getConnection().getConnection().disconnect(reason);
                                }
                            };
                        }
                    });
        }
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    public static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}

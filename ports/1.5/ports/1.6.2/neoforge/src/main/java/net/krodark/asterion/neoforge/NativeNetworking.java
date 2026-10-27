package net.krodark.asterion.neoforge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native NeoForge payload negotiation; shared IDs never register global codecs. */
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

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        add(CLIENTBOUND, type, codec);
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        add(SERVERBOUND, type, codec);
    }

    private static void add(
            Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> map,
            CustomPacketPayload.Type<?> type,
            StreamCodec<? super RegistryFriendlyByteBuf, ?> codec) {
        if (registered || map.putIfAbsent(type, codec) != null)
            throw new IllegalStateException("Duplicate/late Asterion payload: " + type.id());
    }

    public static <T extends CustomPacketPayload> boolean serverHandler(
            CustomPacketPayload.Type<T> type, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        return SERVER_HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static <T extends CustomPacketPayload> boolean clientHandler(
            CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        return CLIENT_HANDLERS.putIfAbsent(type, handler) == null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register(RegisterPayloadHandlersEvent event) {
        if (registered) throw new IllegalStateException("Native payloads registered twice");
        var registrar = event.registrar("1.5.6");
        CLIENTBOUND.forEach(
                (type, codec) -> {
                    if (SERVERBOUND.containsKey(type))
                        registrar.playBidirectional(
                                (CustomPacketPayload.Type) type,
                                (StreamCodec) codec,
                                NativeNetworking::receive,
                                NativeNetworking::receive);
                    else
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void receive(CustomPacketPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            var handler =
                    (ServerPlayNetworking.PlayPayloadHandler) SERVER_HANDLERS.get(payload.type());
            if (handler != null) handler.receive(payload, new ServerPlayNetworking.Context(player));
        } else ClientDispatch.receive(payload);
    }

    private static final class ClientDispatch {
        @SuppressWarnings({"rawtypes", "unchecked"})
        static void receive(CustomPacketPayload payload) {
            var handler =
                    (ClientPlayNetworking.PlayPayloadHandler) CLIENT_HANDLERS.get(payload.type());
            if (handler != null)
                handler.receive(
                        payload,
                        new ClientPlayNetworking.Context(
                                net.minecraft.client.Minecraft.getInstance()));
        }
    }
}

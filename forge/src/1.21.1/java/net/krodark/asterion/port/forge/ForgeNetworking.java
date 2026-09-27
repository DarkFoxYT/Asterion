package net.krodark.asterion.port.forge;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native Forge transport used by the small Fabric networking compatibility surface. */
public final class ForgeNetworking {
    private static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> CLIENTBOUND = new LinkedHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> SERVERBOUND = new LinkedHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, ServerPlayNetworking.PlayPayloadHandler<?>> SERVER_HANDLERS = new LinkedHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, ClientPlayNetworking.PlayPayloadHandler<?>> CLIENT_HANDLERS = new LinkedHashMap<>();
    private static Channel<CustomPacketPayload> channel;

    private ForgeNetworking() {}

    public static synchronized <T extends CustomPacketPayload> void registerClientbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        requireOpen();
        CLIENTBOUND.put(type, codec);
    }

    public static synchronized <T extends CustomPacketPayload> void registerServerbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        requireOpen();
        SERVERBOUND.put(type, codec);
    }

    public static synchronized <T extends CustomPacketPayload> boolean registerServerHandler(
            CustomPacketPayload.Type<T> type, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        requireOpen();
        return SERVER_HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static synchronized <T extends CustomPacketPayload> boolean registerClientHandler(
            CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        requireOpen();
        return CLIENT_HANDLERS.putIfAbsent(type, handler) == null;
    }

    public static synchronized void bootstrap() {
        if (channel != null) return;
        var protocol = ChannelBuilder.named("asterion:main").networkProtocolVersion(1).payloadChannel().play();
        var clientbound = protocol.clientbound();
        CLIENTBOUND.forEach((type, codec) -> {
            if (!SERVERBOUND.containsKey(type)) addClientbound(clientbound, type, codec);
        });
        var serverbound = protocol.serverbound();
        SERVERBOUND.forEach((type, codec) -> {
            if (!CLIENTBOUND.containsKey(type)) addServerbound(serverbound, type, codec);
        });
        var bidirectional = protocol.bidirectional();
        CLIENTBOUND.forEach((type, codec) -> {
            if (SERVERBOUND.containsKey(type)) addBidirectional(bidirectional, type, codec);
        });
        channel = serverbound.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addBidirectional(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
                                          CustomPacketPayload.Type<?> type,
                                          StreamCodec<? super RegistryFriendlyByteBuf, ?> codec) {
        flow.addMain((CustomPacketPayload.Type) type, (StreamCodec) codec, (payload, context) -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) dispatchServer(type, payload, sender);
            else dispatchClient(type, payload);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addClientbound(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
                                       CustomPacketPayload.Type<?> type,
                                       StreamCodec<? super RegistryFriendlyByteBuf, ?> codec) {
        flow.addMain((CustomPacketPayload.Type) type, (StreamCodec) codec, (payload, context) -> {
            dispatchClient(type, payload);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatchClient(CustomPacketPayload.Type<?> type, CustomPacketPayload payload) {
        var handler = (ClientPlayNetworking.PlayPayloadHandler) CLIENT_HANDLERS.get(type);
        if (handler == null) return;
        Minecraft client = Minecraft.getInstance();
        handler.receive(payload, new ClientPlayNetworking.Context() {
                @Override public Minecraft client() { return client; }
                @Override public net.minecraft.client.player.LocalPlayer player() { return client.player; }
                @Override public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
                    return new net.fabricmc.fabric.api.networking.v1.PacketSender() {
                        @Override public net.minecraft.network.protocol.Packet<?> createPacket(CustomPacketPayload reply) {
                            return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(reply);
                        }
                        @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet,
                                net.minecraft.network.PacketSendListener listener) {
                            if (client.getConnection() != null) client.getConnection().getConnection().send(packet, listener);
                        }
                        @Override public void disconnect(net.minecraft.network.chat.Component reason) {
                            if (client.getConnection() != null) client.getConnection().getConnection().disconnect(reason);
                        }
                    };
                }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addServerbound(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
                                       CustomPacketPayload.Type<?> type,
                                       StreamCodec<? super RegistryFriendlyByteBuf, ?> codec) {
        flow.addMain((CustomPacketPayload.Type) type, (StreamCodec) codec, (payload, forgeContext) -> {
            ServerPlayer player = forgeContext.getSender();
            if (player == null) return;
            dispatchServer(type, payload, player);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatchServer(CustomPacketPayload.Type<?> type, CustomPacketPayload payload, ServerPlayer player) {
        var handler = (ServerPlayNetworking.PlayPayloadHandler) SERVER_HANDLERS.get(type);
        if (handler != null) handler.receive(payload, new ServerPlayNetworking.Context() {
                @Override public net.minecraft.server.MinecraftServer server() { return player.getServer(); }
                @Override public ServerPlayer player() { return player; }
                @Override public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
                    return ServerPlayNetworking.getSender(player);
                }
        });
    }

    public static boolean canSend(ServerPlayer player) {
        return channel != null && channel.isRemotePresent(player.connection.getConnection());
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        requireBuilt().send(payload, PacketDistributor.PLAYER.with(player));
    }

    public static boolean canSendToServer() {
        Minecraft client = Minecraft.getInstance();
        return channel != null && client.getConnection() != null
                && channel.isRemotePresent(client.getConnection().getConnection());
    }

    public static void sendToServer(CustomPacketPayload payload) {
        requireBuilt().send(payload, PacketDistributor.SERVER.noArg());
    }

    public static Channel<CustomPacketPayload> channel() {
        return requireBuilt();
    }

    private static Channel<CustomPacketPayload> requireBuilt() {
        if (channel == null) throw new IllegalStateException("Asterion Forge networking has not been bootstrapped");
        return channel;
    }

    private static void requireOpen() {
        if (channel != null) throw new IllegalStateException("Asterion packet registration occurred after channel bootstrap");
    }
}

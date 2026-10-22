package net.krodark.asterion.port.forge.nativelegacy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;

/** Native 1.20.1 transport for the shared, versioned payload codecs. */
public final class NativeLegacyNetwork {
    private static final String PROTOCOL = "1.5.6";
    private static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    new ResourceLocation("asterion", "play"),
                    () -> PROTOCOL,
                    PROTOCOL::equals,
                    PROTOCOL::equals);
    private static final Map<
                    ResourceLocation,
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler>
            SERVER = new HashMap<>();
    private static final Map<
                    ResourceLocation,
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                            .PlayChannelHandler>
            CLIENT = new HashMap<>();
    private static boolean initialized;

    private NativeLegacyNetwork() {}

    public static void initialize() {
        if (initialized) return;
        CHANNEL.registerMessage(
                0,
                Envelope.class,
                NativeLegacyNetwork::encode,
                NativeLegacyNetwork::decode,
                NativeLegacyNetwork::receive);
        initialized = true;
    }

    public static boolean server(
            ResourceLocation id,
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler handler) {
        return SERVER.putIfAbsent(id, handler) == null;
    }

    public static boolean client(
            ResourceLocation id,
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.PlayChannelHandler
                    handler) {
        return CLIENT.putIfAbsent(id, handler) == null;
    }

    public static boolean canSend(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static boolean canSendToServer() {
        return ClientConnection.present();
    }

    public static void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), copy(id, data));
    }

    public static void sendToServer(ResourceLocation id, FriendlyByteBuf data) {
        CHANNEL.sendToServer(copy(id, data));
    }

    private record Envelope(ResourceLocation id, byte[] data) {}

    private static Envelope copy(ResourceLocation id, FriendlyByteBuf buffer) {
        try {
            byte[] data = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), data);
            return new Envelope(id, data);
        } finally {
            buffer.release();
        }
    }

    private static void encode(Envelope packet, FriendlyByteBuf output) {
        output.writeResourceLocation(packet.id());
        output.writeByteArray(packet.data());
    }

    private static Envelope decode(FriendlyByteBuf input) {
        return new Envelope(input.readResourceLocation(), input.readByteArray(1 << 20));
    }

    private static void receive(
            Envelope packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(
                () -> {
                    var sender = context.getSender();
                    if (sender != null) {
                        if (packet.data().length > 32768) {
                            sender.connection.disconnect(
                                    net.minecraft.network.chat.Component.literal(
                                            "Asterion payload exceeds the serverbound limit"));
                            return;
                        }
                        var handler = SERVER.get(packet.id());
                        if (handler != null) {
                            var input =
                                    new FriendlyByteBuf(
                                            io.netty.buffer.Unpooled.wrappedBuffer(packet.data()));
                            try {
                                handler.receive(
                                        sender.server, sender, sender.connection, input, null);
                            } finally {
                                input.release();
                            }
                        }
                    } else ClientConnection.receive(packet);
                });
        context.setPacketHandled(true);
    }

    private static final class ClientConnection {
        static boolean present() {
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            return connection != null && CHANNEL.isRemotePresent(connection.getConnection());
        }

        static void receive(Envelope packet) {
            var handler = CLIENT.get(packet.id());
            if (handler == null) return;
            var client = net.minecraft.client.Minecraft.getInstance();
            var input = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(packet.data()));
            try {
                handler.receive(client, client.getConnection(), input, null);
            } finally {
                input.release();
            }
        }
    }
}

package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Registers shared payload definitions on one native Forge play channel. */
public final class PayloadTypeRegistry {
    private static final List<Entry<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Entry<?>> SERVERBOUND = new ArrayList<>();
    private static final Registry CLIENTBOUND_REGISTRY = new Registry(CLIENTBOUND);
    private static final Registry SERVERBOUND_REGISTRY = new Registry(SERVERBOUND);
    private static Channel<CustomPacketPayload> channel;

    private PayloadTypeRegistry() {}

    public static Registry clientboundPlay() { return CLIENTBOUND_REGISTRY; }
    public static Registry serverboundPlay() { return SERVERBOUND_REGISTRY; }

    public static final class Registry {
        private final List<Entry<?>> entries;
        private Registry(List<Entry<?>> entries) { this.entries = entries; }

        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
                                                               StreamCodec<RegistryFriendlyByteBuf, T> codec) {
            if (channel != null) throw new IllegalStateException("Payload registered after Forge channel build: " + type.id());
            entries.add(new Entry<>(type, codec));
        }
    }

    private record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                         StreamCodec<RegistryFriendlyByteBuf, T> codec) {}

    public static void finishRegistration() {
        if (channel != null) return;
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named("asterion:play").networkProtocolVersion(1).payloadChannel().play();
        Set<Object> serverboundIds = new HashSet<>();
        SERVERBOUND.forEach(entry -> serverboundIds.add(entry.type().id()));
        Set<Object> bothIds = new HashSet<>();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> bidirectional = play.bidirectional();
        for (Entry<?> entry : CLIENTBOUND) {
            if (serverboundIds.contains(entry.type().id())) {
                add(bidirectional, entry, true);
                bothIds.add(entry.type().id());
            }
        }
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> clientbound = play.clientbound();
        for (Entry<?> entry : CLIENTBOUND) if (!bothIds.contains(entry.type().id())) add(clientbound, entry, true);
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> serverbound = play.serverbound();
        for (Entry<?> entry : SERVERBOUND) if (!bothIds.contains(entry.type().id())) add(serverbound, entry, false);
        channel = serverbound.build();
    }

    private static <T extends CustomPacketPayload> void add(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
                                                              Entry<T> entry, boolean toClient) {
        flow.add(entry.type(), entry.codec(), (payload, context) -> dispatch(payload, context, toClient));
    }

    private static void dispatch(CustomPacketPayload payload, CustomPayloadEvent.Context context, boolean toClient) {
        if (context.isClientSide()) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.dispatch(payload, context);
        } else {
            ServerPlayNetworking.dispatch(payload, context);
        }
        context.setPacketHandled(true);
    }

    public static Channel<CustomPacketPayload> channel() {
        if (channel == null) throw new IllegalStateException("Forge payload channel not built");
        return channel;
    }
}

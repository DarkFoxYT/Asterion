package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerPlayConnectionEvents {
    private static final CopyOnWriteArrayList<Init> INITS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Join> JOINS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Disconnect> DISCONNECTS = new CopyOnWriteArrayList<>();
    public static final Event<Init> INIT = INITS::add;
    public static final Event<Join> JOIN = JOINS::add;
    public static final Event<Disconnect> DISCONNECT = DISCONNECTS::add;

    private ServerPlayConnectionEvents() {}

    public static void fireJoin(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        INITS.forEach(listener -> listener.onPlayInit(handler, server));
        PacketSender sender = ServerPlayNetworking.getSender(handler.getPlayer());
        JOINS.forEach(listener -> listener.onPlayReady(handler, sender, server));
    }

    public static void fireDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        DISCONNECTS.forEach(listener -> listener.onPlayDisconnect(handler, server));
    }

    @FunctionalInterface public interface Init {
        void onPlayInit(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
    @FunctionalInterface public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }
    @FunctionalInterface public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
}

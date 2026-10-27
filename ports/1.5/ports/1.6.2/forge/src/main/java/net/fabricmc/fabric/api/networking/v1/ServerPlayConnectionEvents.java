package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Forge player connection event adapter for shared handlers. */
public final class ServerPlayConnectionEvents {
    public static final JoinEvent JOIN = new JoinEvent();
    public static final DisconnectEvent DISCONNECT = new DisconnectEvent();

    private ServerPlayConnectionEvents() {}

    @FunctionalInterface public interface JoinCallback {
        void onJoin(ServerGamePacketListenerImpl handler, Object sender, MinecraftServer server);
    }
    @FunctionalInterface public interface DisconnectCallback {
        void onDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final class JoinEvent {
        public void register(JoinCallback callback) {
            PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
                if (event.getEntity() instanceof ServerPlayer player)
                    callback.onJoin(player.connection, null, player.level().getServer());
            });
        }
    }
    public static final class DisconnectEvent {
        public void register(DisconnectCallback callback) {
            PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
                if (event.getEntity() instanceof ServerPlayer player)
                    callback.onDisconnect(player.connection, player.level().getServer());
            });
        }
    }
}

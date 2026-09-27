package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Forge respawn adapter preserving the old player until respawn completes. */
public final class ServerPlayerEvents {
    private static final Map<UUID, Previous> PREVIOUS = new ConcurrentHashMap<>();
    private static final List<Callback> CALLBACKS = new CopyOnWriteArrayList<>();
    public static final AfterRespawn AFTER_RESPAWN = new AfterRespawn();

    static {
        PlayerEvent.Clone.BUS.addListener(event -> {
            if (event.getOriginal() instanceof ServerPlayer oldPlayer
                    && event.getEntity() instanceof ServerPlayer newPlayer)
                PREVIOUS.put(newPlayer.getUUID(), new Previous(oldPlayer, !event.isWasDeath()));
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> {
            if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
            Previous previous = PREVIOUS.remove(newPlayer.getUUID());
            if (previous != null)
                CALLBACKS.forEach(callback -> callback.onRespawn(previous.oldPlayer(), newPlayer, previous.alive()));
        });
    }

    private ServerPlayerEvents() {}
    private record Previous(ServerPlayer oldPlayer, boolean alive) {}

    @FunctionalInterface public interface Callback {
        void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }

    public static final class AfterRespawn {
        public void register(Callback callback) {
            CALLBACKS.add(callback);
        }
    }
}

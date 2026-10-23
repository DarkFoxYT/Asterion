package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerPlayerEvents {
    private static final CopyOnWriteArrayList<AfterRespawn> AFTER = new CopyOnWriteArrayList<>();
    private static final java.util.Map<java.util.UUID, Previous> PENDING = new java.util.HashMap<>();
    public static final Event<AfterRespawn> AFTER_RESPAWN = AFTER::add;

    private ServerPlayerEvents() {}

    private record Previous(ServerPlayer player, boolean alive) {}

    public static void rememberRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        PENDING.put(newPlayer.getUUID(), new Previous(oldPlayer, alive));
    }

    public static void completeRespawn(ServerPlayer player) {
        Previous previous = PENDING.remove(player.getUUID());
        if (previous != null) fireAfterRespawn(previous.player(), player, previous.alive());
    }

    public static void cancelRespawn(ServerPlayer player) { PENDING.remove(player.getUUID()); }
    public static void clearRespawns() { PENDING.clear(); }

    public static void fireAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        AFTER.forEach(listener -> listener.afterRespawn(oldPlayer, newPlayer, alive));
    }

    @FunctionalInterface public interface AfterRespawn {
        void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }
}

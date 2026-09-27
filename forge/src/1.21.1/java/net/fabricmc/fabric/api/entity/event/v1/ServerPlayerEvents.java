package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerPlayerEvents {
    private static final CopyOnWriteArrayList<AfterRespawn> AFTER = new CopyOnWriteArrayList<>();
    public static final Event<AfterRespawn> AFTER_RESPAWN = AFTER::add;

    private ServerPlayerEvents() {}

    public static void fireAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        AFTER.forEach(listener -> listener.afterRespawn(oldPlayer, newPlayer, alive));
    }

    @FunctionalInterface public interface AfterRespawn {
        void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }
}

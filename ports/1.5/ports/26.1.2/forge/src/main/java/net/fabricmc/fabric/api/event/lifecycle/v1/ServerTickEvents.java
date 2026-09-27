package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;

import java.util.function.Consumer;

/** Forge server tick adapter for shared event registrations. */
public final class ServerTickEvents {
    public static final EndServerTick END_SERVER_TICK = new EndServerTick();

    private ServerTickEvents() {}

    public static final class EndServerTick {
        public void register(Consumer<MinecraftServer> callback) {
            TickEvent.ServerTickEvent.Post.BUS.addListener(event -> callback.accept(event.server()));
        }
    }
}

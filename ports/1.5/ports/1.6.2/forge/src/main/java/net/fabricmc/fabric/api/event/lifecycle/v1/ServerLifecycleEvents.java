package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.function.Consumer;

/** Forge server lifecycle adapter for shared event registrations. */
public final class ServerLifecycleEvents {
    public static final Started SERVER_STARTED = new Started();
    public static final Stopping SERVER_STOPPING = new Stopping();
    public static final Stopped SERVER_STOPPED = new Stopped();

    private ServerLifecycleEvents() {}

    public static final class Started {
        public void register(Consumer<MinecraftServer> callback) {
            ServerStartedEvent.BUS.addListener(event -> callback.accept(event.getServer()));
        }
    }
    public static final class Stopping {
        public void register(Consumer<MinecraftServer> callback) {
            ServerStoppingEvent.BUS.addListener(event -> callback.accept(event.getServer()));
        }
    }
    public static final class Stopped {
        public void register(Consumer<MinecraftServer> callback) {
            ServerStoppedEvent.BUS.addListener(event -> callback.accept(event.getServer()));
        }
    }
}

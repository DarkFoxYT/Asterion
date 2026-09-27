package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.List;

public final class ServerLifecycleEvents {
    private static final List<ServerStarted> STARTED = new ArrayList<>();
    private static final List<ServerStopping> STOPPING = new ArrayList<>();
    private static final List<ServerStopped> STOPPED = new ArrayList<>();
    public static final Event<ServerStarted> SERVER_STARTED = STARTED::add;
    public static final Event<ServerStopping> SERVER_STOPPING = STOPPING::add;
    public static final Event<ServerStopped> SERVER_STOPPED = STOPPED::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> STARTED.forEach(it -> it.onServerStarted(event.getServer())));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> STOPPING.forEach(it -> it.onServerStopping(event.getServer())));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> STOPPED.forEach(it -> it.onServerStopped(event.getServer())));
    }

    private ServerLifecycleEvents() {}
    @FunctionalInterface public interface ServerStarted { void onServerStarted(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopping { void onServerStopping(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopped { void onServerStopped(MinecraftServer server); }
}

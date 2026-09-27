package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

public final class ServerTickEvents {
    private static final List<EndTick> END = new ArrayList<>();
    public static final Event<EndTick> END_SERVER_TICK = END::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) ->
                END.forEach(listener -> listener.onEndTick(event.getServer())));
    }

    private ServerTickEvents() {}
    @FunctionalInterface public interface EndTick { void onEndTick(MinecraftServer server); }
}

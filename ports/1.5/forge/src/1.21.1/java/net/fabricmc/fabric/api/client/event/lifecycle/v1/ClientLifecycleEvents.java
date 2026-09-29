package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;

import java.util.ArrayList;
import java.util.List;

/** Bridge shared renderer cleanup to Forge's client shutdown event. */
public final class ClientLifecycleEvents {
    private static final List<ClientStopping> STOPPING = new ArrayList<>();
    public static final Event<ClientStopping> CLIENT_STOPPING = STOPPING::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) ->
                STOPPING.forEach(listener -> listener.onClientStopping(Minecraft.getInstance())));
    }

    private ClientLifecycleEvents() { }
    @FunctionalInterface public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }
}

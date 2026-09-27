package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.GameShuttingDownEvent;

import java.util.function.Consumer;

/** Forge client shutdown adapter for shared resource cleanup. */
public final class ClientLifecycleEvents {
    public static final ClientStopping CLIENT_STOPPING = new ClientStopping();
    private ClientLifecycleEvents() {}

    public static final class ClientStopping {
        public void register(Consumer<Minecraft> callback) {
            GameShuttingDownEvent.BUS.addListener(event -> callback.accept(Minecraft.getInstance()));
        }
    }
}

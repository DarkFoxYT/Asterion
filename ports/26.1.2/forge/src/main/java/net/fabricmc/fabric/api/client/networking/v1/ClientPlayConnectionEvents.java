package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/** Forge client disconnect adapter for shared texture cleanup. */
public final class ClientPlayConnectionEvents {
    public static final Disconnect DISCONNECT = new Disconnect();
    private ClientPlayConnectionEvents() {}

    @FunctionalInterface public interface Callback { void onDisconnect(Object handler, Minecraft client); }

    public static final class Disconnect {
        public void register(Callback callback) {
            ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event ->
                    callback.onDisconnect(event.getConnection(), Minecraft.getInstance()));
        }
    }
}

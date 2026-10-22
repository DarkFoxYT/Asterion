package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientPlayConnectionEvents {
    public static final Event<Disconnect> DISCONNECT =
            new Event<>(
                    listener ->
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                                    (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
                                                            .LoggingOut
                                                    event) ->
                                            listener.onPlayDisconnect(
                                                    Minecraft.getInstance().getConnection(),
                                                    Minecraft.getInstance())));

    private ClientPlayConnectionEvents() {}

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }
}

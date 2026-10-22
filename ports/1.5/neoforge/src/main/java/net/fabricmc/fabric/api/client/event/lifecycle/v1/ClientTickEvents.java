package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK =
            new Event<>(
                    listener ->
                            NeoForge.EVENT_BUS.addListener(
                                    (ClientTickEvent.Post event) ->
                                            listener.onEndTick(Minecraft.getInstance())));

    private ClientTickEvents() {}

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}

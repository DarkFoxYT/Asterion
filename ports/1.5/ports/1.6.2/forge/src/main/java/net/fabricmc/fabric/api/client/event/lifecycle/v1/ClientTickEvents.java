package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

import java.util.function.Consumer;

/** Forge client tick adapter for shared rendering and input systems. */
public final class ClientTickEvents {
    public static final EndClientTick END_CLIENT_TICK = new EndClientTick();
    private ClientTickEvents() {}

    public static final class EndClientTick {
        public void register(Consumer<Minecraft> callback) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(event -> callback.accept(Minecraft.getInstance()));
        }
    }
}

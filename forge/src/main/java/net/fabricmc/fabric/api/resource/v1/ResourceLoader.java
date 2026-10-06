package net.fabricmc.fabric.api.resource.v1;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

/** Forge adapter for the shared client's resource-reload cleanup hook. */
public final class ResourceLoader {
    private static final ResourceLoader CLIENT = new ResourceLoader();
    private ResourceLoader() { }
    public static ResourceLoader get(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) throw new IllegalArgumentException("Client resource loader expected");
        return CLIENT;
    }
    public void registerReloadListener(Identifier id, PreparableReloadListener listener) {
        RegisterClientReloadListenersEvent.BUS.addListener(event -> event.registerReloadListener(listener));
    }
}

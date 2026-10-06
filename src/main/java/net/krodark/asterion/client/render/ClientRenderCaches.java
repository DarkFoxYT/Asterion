package net.krodark.asterion.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.*;
import net.krodark.asterion.client.render.post.AmneticPostBuffers;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Release owned GPU meshes/targets and optional frame copies at resource/world boundaries. */
public final class ClientRenderCaches {
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static int ticks;
    private ClientRenderCaches() { }
    public static void clear() {
        AmneticBoneEmission.clearCaches();
        AsterionEmissiveBuffer.clearCaches();
        EmissiveBoneMesh.clearCache();
        AmneticPostBuffers.clearCaches();
        net.krodark.asterion.client.render.post.AsterionPostEffects.clearCameraCache();
        TextureFrameCaches.releaseAll();
    }
    public static void initialize() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Asterion.id("render_caches"),
                (ResourceManagerReloadListener)resources->clear());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(level!=client.level) {clear();level=client.level;}
            if(++ticks>=20) {ticks=0;AmneticBoneEmission.trimIdle(System.nanoTime());}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{clear();level=null;}));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{clear();level=null;});
    }
}

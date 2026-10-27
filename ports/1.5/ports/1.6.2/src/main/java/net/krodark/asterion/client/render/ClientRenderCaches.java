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
    private static boolean refreshRecipeSearch;
    private ClientRenderCaches() { }
    public static void clear() {
        FrameRenderAccess.clear();
        com.meekdev.amnetic.client.pipeline.internal.LayerSystem.INSTANCE.dispose();
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
            if(level!=client.level) {clear();level=client.level;refreshRecipeSearch=true;}
            if (refreshRecipeSearch && level != null && client.player != null && client.getConnection() != null) {
                // Vanilla's session reloader otherwise closes over the world from the first recipe sync.
                client.getConnection().searchTrees().updateRecipes(client.player.getRecipeBook(), level);
                refreshRecipeSearch = false;
            }
            if(++ticks>=20) {ticks=0;AmneticBoneEmission.trimIdle(System.nanoTime());}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();level=null;});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{clear();level=null;});
    }
}

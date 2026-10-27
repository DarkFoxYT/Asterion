package com.meekdev.amnetic;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.render.LevelCamera;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Native loader bootstrap for the supplied 1.21.11 renderer; rendering classes stay unchanged. */
@Mod(value = Amnetic.MOD_ID, dist = Dist.CLIENT)
public final class Amnetic {
    public static final String MOD_ID = "amnetic";

    public Amnetic(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(AmneticClient::initialize));
        modBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "shaders"),
                (ResourceManagerReloadListener) manager -> AmneticClient.reloadResources()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                AmneticClient.endClientTick(net.minecraft.client.Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> AmneticClient.disconnect());
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> AmneticClient.stopping());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel event) -> {
            // Raw GL work must run after the level's render pass has ended.
            LevelCamera camera = LevelCamera.main();
            if (camera == null) return;
            AmneticClient.beforeTranslucent(camera);
            AmneticClient.endMain(camera);
        });
    }
}

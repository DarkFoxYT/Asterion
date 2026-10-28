package com.meekdev.amnetic;

import com.meekdev.amnetic.client.AmneticClient;
import com.meekdev.amnetic.client.render.LevelCamera;
import com.meekdev.amnetic.client.ui.AmneticEditorBridge;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("amnetic")
public final class Amnetic {
    public static final String MOD_ID = "amnetic";

    public Amnetic(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        AmneticClient.initialize();
        RegisterKeyMappingsEvent.BUS.addListener(AmneticEditorBridge::registerKeyMapping);
        RegisterClientReloadListenersEvent.BUS.addListener(event -> event.registerReloadListener(
                (ResourceManagerReloadListener) manager -> AmneticClient.reloadResources()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> AmneticClient.endClientTick(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> AmneticClient.disconnect());
        // 26.2 constructs LevelRenderer before client mods. Its one-time
        // AddFramePassEvent has already fired by the time this bootstrap runs.
        new AddFramePassEvent().addPass(Identifier.fromNamespaceAndPath(MOD_ID, "main"), new FramePassManager.PassDefinition() {
                @Override public void extracts(LevelTargetBundle targets, FramePass pass, DeltaTracker delta) {
                    targets.main = pass.readsAndWrites(targets.main);
                    // Amnetic also writes externally owned framebuffer targets.
                    pass.disableCulling();
                }
                @Override public void executes(LevelRenderState state) {
                    LevelCamera camera = LevelCamera.of(state.cameraRenderState);
                    AmneticClient.beforeTranslucent(camera);
                    AmneticClient.endMain(camera);
                }
        });
    }
}

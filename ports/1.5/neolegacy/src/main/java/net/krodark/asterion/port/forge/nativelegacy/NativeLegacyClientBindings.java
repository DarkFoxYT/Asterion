package net.krodark.asterion.port.forge.nativelegacy;

import net.minecraftforge.eventbus.api.IEventBus;

final class NativeLegacyClientBindings {
    private NativeLegacyClientBindings() {}

    static void register(IEventBus modBus) {
        modBus.addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                        event.enqueueWork(
                                net.krodark.asterion.port.forge.AsterionLegacyForgeClient
                                        ::initialize));
        modBus.addListener(
                net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry::registerNative);
        modBus.addListener(
                net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
                        ::registerNative);
        modBus.addListener(
                (net.minecraftforge.client.event.RegisterShadersEvent event) -> {
                    try {
                        net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
                                .fire(
                                        (id, format, loaded) ->
                                                event.registerShader(
                                                        new net.minecraft.client.renderer
                                                                .ShaderInstance(
                                                                event.getResourceProvider(),
                                                                id,
                                                                format),
                                                        loaded));
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                });
        modBus.addListener(
                (net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) ->
                        event.registerAboveAll(
                                "asterion_hud",
                                (gui, graphics, delta, width, height) ->
                                        net.fabricmc.fabric.api.client.rendering.v1
                                                .HudRenderCallback.fire(graphics, delta)));
    }
}

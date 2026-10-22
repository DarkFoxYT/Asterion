package net.krodark.asterion.neoforge;

import net.krodark.asterion.client.AsterionClient;
import net.krodark.asterion.fluid.HeavyWater;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;

final class AsterionNeoForgeClient {
    private AsterionNeoForgeClient() { }

    static void register(IEventBus modBus) {
        modBus.addListener(AsterionNeoForgeClient::registerFluidModels);
        modBus.addListener(net.fabricmc.fabric.api.resource.ResourceManagerHelper::registerClient);
        modBus.addListener(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper::register);
        modBus.addListener(net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry::register);
        modBus.addListener(net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback::register);
    }

    static void initializeSharedClient() {
        new AsterionClient().onInitializeClient();
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        FluidModel.Unbaked model = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                BlockTintSources.constant(HeavyWater.COLOR));
        event.register(model, HeavyWater.STILL, HeavyWater.FLOWING);
        event.register(model, HeavyWater.FLUID);
    }
}

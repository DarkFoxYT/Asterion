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
    }

    static void initializeSharedClient() {
        new AsterionClient().onInitializeClient();
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        FluidModel.Unbaked model = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                net.neoforged.neoforge.client.fluid.FluidTintSources.of(BlockTintSources.constant(HeavyWater.COLOR)));
        event.register(model, HeavyWater.STILL, HeavyWater.FLOWING);
        event.register(model, HeavyWater.FLUID);
    }
}

package net.krodark.asterion.client.render;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.krodark.asterion.fluid.HeavyWater;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

public final class HeavyWaterRendering {
    private HeavyWaterRendering() { }
    public static void initialize() {
        FluidModel.Unbaked model = new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                BlockTintSources.constant(HeavyWater.COLOR));
        FluidRenderingRegistry.register(HeavyWater.STILL, HeavyWater.FLOWING, model);
        FluidRenderingRegistry.register(HeavyWater.FLUID, model);
    }
}

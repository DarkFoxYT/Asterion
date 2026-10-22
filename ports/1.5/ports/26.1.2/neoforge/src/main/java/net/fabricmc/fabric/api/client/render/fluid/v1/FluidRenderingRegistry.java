package net.fabricmc.fabric.api.client.render.fluid.v1;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.material.Fluid;

public final class FluidRenderingRegistry {
    private static final java.util.Map<Fluid, FluidModel.Unbaked> MODELS =
            new java.util.LinkedHashMap<>();

    private FluidRenderingRegistry() {}

    public static void register(Fluid fluid, FluidModel.Unbaked model) {
        MODELS.put(fluid, model);
    }

    public static void register(Fluid still, Fluid flowing, FluidModel.Unbaked model) {
        register(still, model);
        register(flowing, model);
    }

    public static void registerNative(
            net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        MODELS.forEach((fluid, model) -> event.register(model, fluid));
    }
}

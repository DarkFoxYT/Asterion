package net.fabricmc.fabric.api.client.render.fluid.v1;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.ModelEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Bakes shared heavy-water fluid models through Forge's fluid model event. */
public final class FluidRenderingRegistry {
    private static final Map<Fluid, FluidModel.Unbaked> MODELS = new IdentityHashMap<>();

    static {
        ModelEvent.BakeFluidModels.BUS.addListener(event -> MODELS.forEach((fluid, model) ->
                event.register(fluid, model.bake(event.materials(), () -> "asterion fluid " + fluid))));
    }

    private FluidRenderingRegistry() {}

    public static void register(Fluid fluid, FluidModel.Unbaked model) {
        MODELS.put(fluid, model);
    }

    public static void register(Fluid still, Fluid flowing, FluidModel.Unbaked model) {
        register(still, model);
        register(flowing, model);
    }
}

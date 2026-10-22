package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DimensionRenderingRegistry {
    private static final java.util.Map<ResourceLocation, DimensionSpecialEffects> EFFECTS =
            new java.util.HashMap<>();

    private DimensionRenderingRegistry() {}

    public static void registerDimensionEffects(
            ResourceLocation id, DimensionSpecialEffects effects) {
        EFFECTS.put(id, effects);
    }

    public static void registerCloudRenderer(
            ResourceKey<Level> dimension, CloudRenderer renderer) {}

    public static void registerNative(
            net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        EFFECTS.forEach(event::register);
    }

    @FunctionalInterface
    public interface CloudRenderer {
        void render(WorldRenderContext context);
    }
}

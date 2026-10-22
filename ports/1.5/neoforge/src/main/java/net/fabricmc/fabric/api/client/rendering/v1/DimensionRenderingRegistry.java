package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DimensionRenderingRegistry {
    private DimensionRenderingRegistry() {}

    // Registered by AsterionNeoForgeClientModEvents in the native lifecycle.
    public static void registerDimensionEffects(
            ResourceLocation id, DimensionSpecialEffects effects) {}

    // Asterion's native DimensionSpecialEffects already suppresses maze clouds.
    public static void registerCloudRenderer(
            ResourceKey<Level> dimension, CloudRenderer renderer) {}

    @FunctionalInterface
    public interface CloudRenderer {
        void render(WorldRenderContext context);
    }
}

package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Forge applies these shared biome additions through equivalent data pack modifiers. */
public final class BiomeModifications {
    private BiomeModifications() {}

    public static void addFeature(BiomeSelectors.Selector selector, GenerationStep.Decoration step,
                                  ResourceKey<PlacedFeature> feature) {}

    public static void addSpawn(BiomeSelectors.Selector selector, MobCategory category,
                                EntityType<?> entity, int weight, int minCount, int maxCount) {}
}

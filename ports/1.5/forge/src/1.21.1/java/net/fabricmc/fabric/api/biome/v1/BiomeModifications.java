package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.function.Predicate;

public final class BiomeModifications {
    private BiomeModifications() {}
    public static void addFeature(Predicate<BiomeSelectionContext> selector,
            GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature) {
        // Asterion's authored dimensions place these features directly; Forge's
        // biome modifier registry is data driven and cannot be mutated here.
    }
    public static void addSpawn(Predicate<BiomeSelectionContext> selector, MobCategory category,
            EntityType<?> type, int weight, int minGroupSize, int maxGroupSize) {
        // Authored Asterion encounters and spawn placement rules own these spawns.
    }
}

package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Minimal selection view used while translating Fabric biome declarations on Forge. */
public interface BiomeSelectionContext {
    ResourceKey<Biome> getBiomeKey();
    Biome getBiome();
    Holder<Biome> getBiomeRegistryEntry();
    default boolean hasTag(net.minecraft.tags.TagKey<Biome> tag) { return getBiomeRegistryEntry().is(tag); }
}

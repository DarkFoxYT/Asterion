package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Selector marker; matching Forge biome modifiers live in data/asterion/forge/biome_modifier. */
public final class BiomeSelectors {
    private BiomeSelectors() {}

    public record Selector() {}

    @SafeVarargs
    public static Selector includeByKey(ResourceKey<Biome>... biomes) { return new Selector(); }
    public static Selector tag(TagKey<Biome> biomeTag) { return new Selector(); }
}

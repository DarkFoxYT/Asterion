package net.fabricmc.fabric.api.biome.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;

public final class BiomeSelectors {
    private BiomeSelectors() {}
    public static Predicate<BiomeSelectionContext> all() { return context -> true; }
    public static Predicate<BiomeSelectionContext> tag(TagKey<Biome> tag) { return context -> context.hasTag(tag); }
    @SafeVarargs public static Predicate<BiomeSelectionContext> includeByKey(ResourceKey<Biome>... keys) {
        return includeByKey(Arrays.asList(keys));
    }
    public static Predicate<BiomeSelectionContext> includeByKey(Collection<ResourceKey<Biome>> keys) {
        var selected = new HashSet<>(keys);
        return context -> selected.contains(context.getBiomeKey());
    }
}

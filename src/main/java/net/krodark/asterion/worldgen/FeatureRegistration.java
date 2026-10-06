package net.krodark.asterion.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class FeatureRegistration {
    private FeatureRegistration() { }
    public static <F extends Feature<NoneFeatureConfiguration>> F register(Identifier id, F feature) {
        //? if >=26.3 {
        /*Registry.register(BuiltInRegistries.FEATURE_TYPE, id, feature.codec());
        return feature;
        *///?} else {
        return Registry.register(BuiltInRegistries.FEATURE, id, feature);
        //?}
    }
}

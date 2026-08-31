package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Reconstructs the same authored module plan for each intersecting chunk. */
public final class CatacombFeature extends Feature<NoneFeatureConfiguration> {
    public CatacombFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!context.level().getLevel().dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        AuthoredCatacombs.place(context.level(), ChunkPos.containing(context.origin()));
        return true;
    }
}
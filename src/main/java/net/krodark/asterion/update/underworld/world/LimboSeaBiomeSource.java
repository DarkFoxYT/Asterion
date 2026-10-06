package net.krodark.asterion.update.underworld.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.List;
import java.util.stream.Stream;

public final class LimboSeaBiomeSource extends BiomeSource {
    public static final MapCodec<LimboSeaBiomeSource> CODEC=RecordCodecBuilder.mapCodec(instance ->
            instance.group(Biome.CODEC.listOf().fieldOf("biomes").forGetter(source->source.biomes))
                    .apply(instance,LimboSeaBiomeSource::new));
    private final List<Holder<Biome>> biomes;
    public LimboSeaBiomeSource(List<Holder<Biome>> biomes) {
        if(biomes.size()!=5)throw new IllegalArgumentException("Limbo requires exactly five seas, Styx first");
        this.biomes=List.copyOf(biomes);
    }
    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return biomes.stream(); }
    //? if <26.3 {
    @Override
    //?}
    public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler climate) {
        return biomes.get(LimboSeaRegions.sea(x*4.0,z*4.0).ordinal());
    }

    //? if >=26.3 {
    /*@Override public net.minecraft.world.level.biome.BiomeResolver createResolver(Climate.Sampler sampler) {
        return (x, y, z) -> getNoiseBiome(x, y, z, sampler);
    }
    *///?}
}

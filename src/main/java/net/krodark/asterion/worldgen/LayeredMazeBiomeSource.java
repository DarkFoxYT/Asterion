package net.krodark.asterion.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Real quart-biome assignment for the vertically stacked labyrinth levels. */
public final class LayeredMazeBiomeSource extends BiomeSource {
    public static final MapCodec<LayeredMazeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Biome.CODEC.fieldOf("surface").forGetter(source -> source.surface),
                    Biome.CODEC.fieldOf("catacombs").forGetter(source -> source.catacombs),
                    Biome.CODEC.fieldOf("forge").forGetter(source -> source.forge)
            ).apply(instance, LayeredMazeBiomeSource::new));

    private final Holder<Biome> surface;
    private final Holder<Biome> catacombs;
    private final Holder<Biome> forge;

    public LayeredMazeBiomeSource(Holder<Biome> surface, Holder<Biome> catacombs, Holder<Biome> forge) {
        this.surface = surface;
        this.catacombs = catacombs;
        this.forge = forge;
    }

    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(surface, catacombs, forge);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int blockY = quartY << 2;
        if (blockY <= LabyrinthLevels.FORGE_ROOF_Y) return forge;
        if (blockY < LabyrinthLevels.MAZE_FLOOR_Y) return catacombs;
        return surface;
    }
}

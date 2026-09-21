package net.krodark.asterion.update.underworld.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krodark.asterion.Asterion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.concurrent.CompletableFuture;

public final class UnderworldChunkGenerator extends net.minecraft.world.level.chunk.ChunkGenerator {
    public static final MapCodec<UnderworldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(UnderworldChunkGenerator::settings),
            net.minecraft.world.level.biome.BiomeSource.CODEC.fieldOf("biome_source")
                    .forGetter(UnderworldChunkGenerator::getBiomeSource)
    ).apply(instance, UnderworldChunkGenerator::new));

    private record SeedCache(RandomState random, long seed) { }
    private static volatile SeedCache cache;
    private final FlatLevelGeneratorSettings settings;
    private final FlatLevelSource flat;

    public UnderworldChunkGenerator(FlatLevelGeneratorSettings settings,
                                    net.minecraft.world.level.biome.BiomeSource biomeSource) {
        super(biomeSource, settings::adjustGenerationSettings);
        this.settings = settings;
        this.flat = new FlatLevelSource(settings);
    }

    public FlatLevelGeneratorSettings settings() { return settings; }

    private static long terrainSeed(RandomState random) {
        SeedCache current = cache;
        if (current != null && current.random == random) return current.seed;
        long seed = random.getOrCreateRandomFactory(Asterion.id("underworld_river")).at(0, 0, 0).nextLong();
        cache = new SeedCache(random, seed);
        return seed;
    }

    @Override protected MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() { return CODEC; }
    @Override public net.minecraft.world.level.chunk.ChunkGeneratorStructureState createState(
            net.minecraft.core.HolderLookup<net.minecraft.world.level.levelgen.structure.StructureSet> structures,
            RandomState random, long seed) { return flat.createState(structures, random, seed); }
    @Override public void buildSurface(net.minecraft.server.level.WorldGenRegion region, StructureManager structures,
                                       RandomState random, ChunkAccess chunk) { }
    @Override public void applyCarvers(net.minecraft.server.level.WorldGenRegion region, long seed, RandomState random,
                                       net.minecraft.world.level.biome.BiomeManager biomes, StructureManager structures,
                                       ChunkAccess chunk) { }
    @Override public void spawnOriginalMobs(net.minecraft.server.level.WorldGenRegion region) { }
    @Override public int getSpawnHeight(net.minecraft.world.level.LevelHeightAccessor level) { return UnderworldTerrain.SPAWN_Y; }
    @Override public int getMinY() { return UnderworldTerrain.MIN_Y; }
    @Override public int getGenDepth() { return UnderworldTerrain.MAX_Y - UnderworldTerrain.MIN_Y + 1; }
    @Override public int getSeaLevel() { return UnderworldTerrain.WATER_Y; }
    @Override public int getBaseHeight(int x, int z, net.minecraft.world.level.levelgen.Heightmap.Types type,
                                       net.minecraft.world.level.LevelHeightAccessor level, RandomState random) {
        return UnderworldTerrain.SPAWN_Y;
    }
    @Override public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z,
            net.minecraft.world.level.LevelHeightAccessor level, RandomState random) {
        return flat.getBaseColumn(x, z, level, random);
    }
    @Override public void addDebugScreenInfo(java.util.List<String> lines, RandomState random,
                                             net.minecraft.core.BlockPos pos) {
        lines.add("Asterion 2.0 Styx shoreline");
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        return flat.fillFromNoise(blender, random, structures, chunk).thenApply(generated -> {
            UnderworldTerrain.generate(generated, terrainSeed(random));
            return generated;
        });
    }
}

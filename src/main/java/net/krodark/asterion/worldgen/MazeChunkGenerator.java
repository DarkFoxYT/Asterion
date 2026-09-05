package net.krodark.asterion.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.StructureManager;

import java.util.concurrent.CompletableFuture;

public final class MazeChunkGenerator extends net.minecraft.world.level.chunk.ChunkGenerator {
    private record TerrainSeed(RandomState state, long seed) { }
    private static volatile TerrainSeed cachedTerrainSeed;
    public static final MapCodec<MazeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(MazeChunkGenerator::settings),
                    net.minecraft.world.level.biome.BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(MazeChunkGenerator::getBiomeSource)
            ).apply(instance, MazeChunkGenerator::new));
    private final FlatLevelGeneratorSettings settings;
    private final FlatLevelSource flat;

    public MazeChunkGenerator(FlatLevelGeneratorSettings settings,
                              net.minecraft.world.level.biome.BiomeSource biomeSource) {
        super(biomeSource, settings::adjustGenerationSettings);
        this.settings = settings;
        this.flat = new FlatLevelSource(settings);
    }

    public FlatLevelGeneratorSettings settings() { return settings; }

    /** Keep the established terrain layout; features must use this same derived seed. */
    public static long terrainSeed(RandomState randomState) {
        TerrainSeed cached = cachedTerrainSeed;
        if (cached != null && cached.state == randomState) return cached.seed;
        long seed = randomState.getOrCreateRandomFactory(Asterion.id("maze_layout")).at(0, 0, 0).nextLong();
        cachedTerrainSeed = new TerrainSeed(randomState, seed);
        return seed;
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator> codec() {
        return CODEC;
    }

    @Override public net.minecraft.world.level.chunk.ChunkGeneratorStructureState createState(
            net.minecraft.core.HolderLookup<net.minecraft.world.level.levelgen.structure.StructureSet> structures,
            RandomState random, long seed) { return flat.createState(structures, random, seed); }
    @Override public void buildSurface(net.minecraft.server.level.WorldGenRegion region, StructureManager structures,
                                       RandomState random, ChunkAccess chunk) { flat.buildSurface(region, structures, random, chunk); }
    @Override public void applyCarvers(net.minecraft.server.level.WorldGenRegion region, long seed, RandomState random,
                                       net.minecraft.world.level.biome.BiomeManager biomes, StructureManager structures,
                                       ChunkAccess chunk) { flat.applyCarvers(region, seed, random, biomes, structures, chunk); }
    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel world, ChunkAccess chunk,
                                     StructureManager structures) {
        super.applyBiomeDecoration(world, chunk, structures);
        // Authored rooms are part of the dimension, even when a saved flat configuration disables features.
        AuthoredCatacombs.place(world, chunk.getPos());
        AuthoredForge.place(world, chunk.getPos());
        ForgeDepths.carveAccess(world, chunk.getPos());
    }

    @Override public void spawnOriginalMobs(net.minecraft.server.level.WorldGenRegion region) { flat.spawnOriginalMobs(region); }
    @Override public int getSpawnHeight(net.minecraft.world.level.LevelHeightAccessor level) { return flat.getSpawnHeight(level); }
    @Override public int getMinY() { return flat.getMinY(); }
    @Override public int getGenDepth() { return flat.getGenDepth(); }
    @Override public int getSeaLevel() { return flat.getSeaLevel(); }
    @Override public int getBaseHeight(int x, int z, net.minecraft.world.level.levelgen.Heightmap.Types type,
                                       net.minecraft.world.level.LevelHeightAccessor level, RandomState random) {
        return flat.getBaseHeight(x, z, type, level, random);
    }
    @Override public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z,
            net.minecraft.world.level.LevelHeightAccessor level, RandomState random) {
        return flat.getBaseColumn(x, z, level, random);
    }
    @Override public void addDebugScreenInfo(java.util.List<String> lines, RandomState random,
                                             net.minecraft.core.BlockPos pos) { flat.addDebugScreenInfo(lines, random, pos); }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager,
                                                         ChunkAccess chunk) {
        return flat.fillFromNoise(blender, randomState, structureManager, chunk).thenApply(generated -> {
            long worldSeed = terrainSeed(randomState);
            WorldGenerator.generateMazeChunk(generated, worldSeed);
            return generated;
        });
    }
}

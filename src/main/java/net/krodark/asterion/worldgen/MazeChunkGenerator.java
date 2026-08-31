package net.krodark.asterion.worldgen;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.StructureManager;

import java.util.concurrent.CompletableFuture;

public final class MazeChunkGenerator extends FlatLevelSource {
    private record TerrainSeed(RandomState state, long seed) { }
    private static volatile TerrainSeed cachedTerrainSeed;
    public static final MapCodec<MazeChunkGenerator> CODEC = FlatLevelGeneratorSettings.CODEC
            .fieldOf("settings")
            .xmap(MazeChunkGenerator::new, MazeChunkGenerator::settings);

    public MazeChunkGenerator(FlatLevelGeneratorSettings settings) {
        super(settings);
    }

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

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager,
                                                         ChunkAccess chunk) {
        return super.fillFromNoise(blender, randomState, structureManager, chunk).thenApply(generated -> {
            long worldSeed = terrainSeed(randomState);
            WorldGenerator.generateMazeChunk(generated, worldSeed);
            return generated;
        });
    }
}

package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import java.util.Optional;

public final class MazePits {
    private MazePits() { }
    public static void place(FeaturePlaceContext<NoneFeatureConfiguration> context, ChunkPos chunk, long seed) {
        if (Math.floorMod(chunk.x(), 12) != 6 || Math.floorMod(chunk.z(), 12) != 6) return;
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (Math.abs(x) < 100 && Math.abs(z) < 100) return;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++)
            if (MazeNbtStructures.generationLayout(seed).reserved(x + dx, z + dz)) return;
        var world = context.level(); var level = world.getLevel();
        var pool = world.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
                .getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, Asterion.id("maze_pits/start")));
        int floor = WorldGenerator.mazeFloorHeight(seed, x, z);
        var generation = new Structure.GenerationContext(world.registryAccess(), context.chunkGenerator(),
                context.chunkGenerator().getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                world.getSeed(), chunk, world, b -> true);
        BlockPos anchor = new BlockPos(x, floor, z);
        var plan = JigsawPlacement.addPieces(generation, pool, Optional.of(Asterion.id("maze_pits/surface")), 1,
                anchor, false, Optional.empty(), new JigsawStructure.MaxDistance(15, 32), PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
        if (plan.isEmpty()) return;
        BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), floor - 26, chunk.getMinBlockZ(), chunk.getMaxBlockX(), floor + 1, chunk.getMaxBlockZ());
        for (var piece : plan.get().getPiecesBuilder().build().pieces())
            if (piece instanceof PoolElementStructurePiece pit) {
                // Vanilla lowers rigid starts by their ground delta; keep our named top connector at the surface.
                pit.move(0, pit.getGroundLevelDelta(), 0);
                pit.place(world, level.structureManager(), context.chunkGenerator(),
                        RandomSource.create(seed ^ anchor.asLong()), clip, anchor, false);
            }
        // Make the surface opening visible instead of leaving maze walls floating over the pit.
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            for (int y = floor + 1; y <= floor + 18; y++) world.setBlock(new BlockPos(x + dx, y, z + dz),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
    }
}

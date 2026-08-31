package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.Optional;

/** Rebuilds the same small jigsaw plan for each intersecting chunk, writing only that chunk. */
public final class CatacombFeature extends Feature<NoneFeatureConfiguration> {
    public CatacombFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var world = context.level();
        var level = world.getLevel();
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int tileX = Math.floorDiv(chunk.x(), 2) * 32;
        int tileZ = Math.floorDiv(chunk.z(), 2) * 32;
        var pool = world.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
                .getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, Asterion.id("catacombs/start")));
        var generation = new Structure.GenerationContext(world.registryAccess(), context.chunkGenerator(),
                context.chunkGenerator().getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), world.getSeed(), new ChunkPos(tileX >> 4, tileZ >> 4), world, b -> true);
        BlockPos anchor = new BlockPos(tileX + 16, CatacombLayout.FLOOR_Y + 1, tileZ + 16);
        var plan = JigsawPlacement.addPieces(generation, pool, Optional.of(Asterion.id("catacombs/origin")),
                1, anchor, false, Optional.empty(), new JigsawStructure.MaxDistance(15, 12),
                PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
        if (plan.isEmpty()) return false;
        BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), 3, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), CatacombLayout.ROOF_Y, chunk.getMaxBlockZ());
        for (var piece : plan.get().getPiecesBuilder().build().pieces()) {
            if (piece instanceof PoolElementStructurePiece room && room.getBoundingBox().intersects(clip))
                room.place(world, level.structureManager(), context.chunkGenerator(),
                        RandomSource.create(world.getSeed() ^ anchor.asLong()), clip, anchor, false);
        }
        placeAccess(context, chunk);
        return true;
    }

    private static void placeAccess(FeaturePlaceContext<NoneFeatureConfiguration> context, ChunkPos chunk) {
        long seed = context.level().getLevel().getChunkSource().randomState()
                .getOrCreateRandomFactory(Asterion.id("maze_layout")).at(0, 0, 0).nextLong();
        CatacombEntrances.place(context.level(), chunk, seed);
        MazePits.place(context, chunk, seed);
    }
}

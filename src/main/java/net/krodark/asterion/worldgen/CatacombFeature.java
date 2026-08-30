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
        // Search each selected chunk for an actual open surface corridor, never a wall or landmark.
        if (Math.floorMod(chunk.x(), 8) != 4 || Math.floorMod(chunk.z(), 8) != 4) return;
        var world = context.level();
        long terrainSeed = world.getLevel().getChunkSource().randomState()
                .getOrCreateRandomFactory(Asterion.id("maze_layout")).at(0, 0, 0).nextLong();
        for (int dx = 3; dx <= 12; dx++) for (int dz = 3; dz <= 12; dz++) {
            int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
            if (Math.abs(x) < 96 && Math.abs(z) < 96) continue;
            if (MazeNbtStructures.generationLayout(terrainSeed)
                    .reserved(x, z)) continue;
            int top = -1;
            for (int y = 54; y >= 48; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (world.getBlockState(p).isCollisionShapeFullBlock(world, p)
                        && world.getBlockState(p.above()).isAir() && world.getBlockState(p.above(2)).isAir()) {
                    top = y;
                    break;
                }
            }
            if (top < 0) continue;
            // A lined ladder shaft, with a dry lip and a shallow-water landing. All writes stay local.
            for (int y = CatacombLayout.FLOOR_Y; y <= top; y++) {
                for (int sx = -1; sx <= 1; sx++) for (int sz = -1; sz <= 1; sz++) {
                    BlockPos p = new BlockPos(x + sx, y, z + sz);
                    world.setBlock(p, Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
                }
                if (y > CatacombLayout.WATER_Y)
                    world.setBlock(new BlockPos(x, y, z), Blocks.LADDER.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 2);
            }
            // Join the shaft to the tile's east/west gallery without touching adjacent chunks.
            int galleryZ = chunk.getMinBlockZ() + 1;
            for (int pz = galleryZ; pz <= z; pz++) for (int y = 7; y <= 10; y++)
                world.setBlock(new BlockPos(x, y, pz), (y == 7 ? Blocks.WATER : Blocks.AIR).defaultBlockState(), 2);
            for (int y = 8; y <= top; y++)
                world.setBlock(new BlockPos(x, y, z), Blocks.LADDER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 2);
            world.setBlock(new BlockPos(x + 1, top + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            // A reusable hook is guaranteed at each entrance; no dependence on random loot.
            BlockPos supplies = new BlockPos(x - 1, top + 1, z);
            world.setBlock(supplies, Blocks.BARREL.defaultBlockState(), 2);
            if (world.getBlockEntity(supplies) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
                barrel.setItem(0, new net.minecraft.world.item.ItemStack(Asterion.CATACOMB_GRAPPLING_HOOK));
                barrel.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
                barrel.setChanged();
            }
            return;
        }
    }
}

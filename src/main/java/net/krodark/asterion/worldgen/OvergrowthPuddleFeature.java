package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class OvergrowthPuddleFeature extends Feature<NoneFeatureConfiguration> {
    public OvergrowthPuddleFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int minX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int minZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        for (int attempt = 0; attempt < 40; attempt++) {
            int sampleX = minX + 3 + random.nextInt(10);
            int sampleZ = minZ + 3 + random.nextInt(10);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, sampleX, sampleZ);
            if (floor == null) continue;
            boolean marsh = OvergrowthFeatureSupport.enabled(level, floor, "marsh_pools");
            if (!marsh && !OvergrowthFeatureSupport.enabled(level, floor, "puddles")) continue;
            WallFeed wall = nearbyWall(level, floor);
            if (!hasCleanBasin(level, floor, marsh)) continue;
            carvePuddle(level, floor, random, wall, marsh);
            return true;
        }
        return false;
    }

    private static boolean hasCleanBasin(WorldGenLevel level, BlockPos center, boolean marsh) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            // Marshes can fit curved corridors without a square footprint.
            if (marsh && Math.abs(dx) + Math.abs(dz) > 1) continue;
            BlockPos floor = center.offset(dx, 0, dz);
            if (!OvergrowthFeatureSupport.isMazeFloor(level, floor)
                    || !OvergrowthFeatureSupport.isOpen(level, floor.above())) return false;
        }
        return true;
    }

    private static void carvePuddle(WorldGenLevel level, BlockPos center, RandomSource random,
                                    WallFeed wallFeed, boolean marsh) {
        double longAxis = marsh ? 4.8D + random.nextDouble() * 1.8D
                : 2.05D + random.nextDouble() * 0.65D;
        double shortAxis = marsh ? 3.5D + random.nextDouble() * 1.5D
                : 1.55D + random.nextDouble() * 0.45D;
        boolean stretchX = random.nextBoolean();
        int reach = marsh ? 7 : 3;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            double nx = dx / (stretchX ? longAxis : shortAxis);
            double nz = dz / (stretchX ? shortAxis : longAxis);
            double noise = signedNoise(center, dx, dz) * 0.15D;
            double shape = Math.sqrt(nx * nx + nz * nz) + noise;
            BlockPos pos = center.offset(dx, 0, dz);
            if (!OvergrowthFeatureSupport.canWrite(level, pos) || !OvergrowthFeatureSupport.canWrite(level, pos.below())) continue;
            boolean cleanFloor = OvergrowthFeatureSupport.isMazeFloor(level, pos)
                    && OvergrowthFeatureSupport.isOpen(level, pos.above());
            if (!cleanFloor) continue;

            if (shape <= 0.72D) {
                level.setBlock(pos.below(), Blocks.MUD.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                if (marsh && random.nextFloat() < 0.055F)
                    TaintedPetalsFeature.placeOnWaterSurface(level, pos.above());
            } else if (shape <= 1.03D) {
                level.setBlock(pos.below(), Blocks.MUD.defaultBlockState(), 2);
                level.setBlock(pos, smoothWaterEdge(dx, dz, random), 2);
            } else if (shape <= (marsh ? 1.38D : 1.30D)
                    && random.nextFloat() < (marsh ? 0.88F : 0.74F)) {
                level.setBlock(pos, Blocks.MUD.defaultBlockState(), 2);
            } else if (shape <= (marsh ? 1.72D : 1.58D)
                    && random.nextFloat() < (marsh ? 0.58F : 0.48F)) {
                level.setBlock(pos, random.nextBoolean()
                        ? Asterion.ANCIENT_MOSS.defaultBlockState()
                        : Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState(), 2);
            }
        }

        // Keep the wall seep over the recessed basin.
        if (wallFeed != null && random.nextFloat() < (marsh ? 0.76F : 0.58F)) {
            BlockPos lip = center.relative(wallFeed.direction, wallFeed.distance - 1);
            BlockPos backing = center.relative(wallFeed.direction, wallFeed.distance);
            if (OvergrowthFeatureSupport.canWrite(level, lip.below())
                    && OvergrowthFeatureSupport.canWrite(level, lip.above(3))
                    && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(backing.above()))
                    && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(backing.above(2)))) {
                level.setBlock(lip.above(), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(lip.above(2), Blocks.WATER.defaultBlockState(), 2);
                if (marsh && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(backing.above(3))))
                    level.setBlock(lip.above(3), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(lip.below(), Blocks.MUD.defaultBlockState(), 2);
            }
        }
    }

    private static BlockState smoothWaterEdge(int dx, int dz, RandomSource random) {
        if (random.nextFloat() < 0.56F) {
            return Asterion.ANCIENT_STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    .setValue(BlockStateProperties.WATERLOGGED, true);
        }
        return Asterion.ANCIENT_STONE_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, inwardFacing(dx, dz))
                .setValue(BlockStateProperties.WATERLOGGED, true);
    }

    private static Direction inwardFacing(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static double signedNoise(BlockPos center, int dx, int dz) {
        long bits = mix(center.asLong() ^ dx * 0x9E3779B9L ^ dz * 0x632BE59BL);
        return ((bits >>> 40) & 0xFFFFL) / 32767.5D - 1.0D;
    }

    private static WallFeed nearbyWall(WorldGenLevel level, BlockPos center) {
        for (int distance = 2; distance <= 4; distance++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos wall = center.relative(direction, distance);
                if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(wall.above()))
                        && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(wall.above(2))))
                    return new WallFeed(direction, distance);
            }
        }
        return null;
    }

    private record WallFeed(Direction direction, int distance) { }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}

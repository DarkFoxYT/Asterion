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

/** Small, mud-bottomed Overgrowth puddles; some are fed by a restrained wall seep. */
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
        long roll = mix(level.getSeed() ^ (long) minX * 0xD1B54A32D192ED03L
                ^ (long) minZ * 0x94D049BB133111EBL);
        if (Math.floorMod(roll, 3L) != 0L) return false;

        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level,
                    minX + 3 + random.nextInt(10), minZ + 3 + random.nextInt(10));
            if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "puddles")) continue;
            WallFeed wall = nearbyWall(level, floor);
            if (!hasCleanBasin(level, floor)) continue;
            carvePuddle(level, floor, random, wall);
            return true;
        }
        return false;
    }

    private static boolean hasCleanBasin(WorldGenLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos floor = center.offset(dx, 0, dz);
            if (!OvergrowthFeatureSupport.isMazeFloor(level, floor)
                    || !OvergrowthFeatureSupport.isOpen(level, floor.above())) return false;
        }
        return true;
    }

    private static void carvePuddle(WorldGenLevel level, BlockPos center, RandomSource random,
                                    WallFeed wallFeed) {
        double longAxis = 2.05D + random.nextDouble() * 0.65D;
        double shortAxis = 1.55D + random.nextDouble() * 0.45D;
        boolean stretchX = random.nextBoolean();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double nx = dx / (stretchX ? longAxis : shortAxis);
            double nz = dz / (stretchX ? shortAxis : longAxis);
            double noise = signedNoise(center, dx, dz) * 0.15D;
            double shape = Math.sqrt(nx * nx + nz * nz) + noise;
            BlockPos pos = center.offset(dx, 0, dz);

            if (shape <= 0.72D) {
                level.setBlock(pos.below(), Blocks.MUD.defaultBlockState(), 2);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            } else if (shape <= 1.03D) {
                level.setBlock(pos.below(), Blocks.MUD.defaultBlockState(), 2);
                level.setBlock(pos, smoothWaterEdge(dx, dz, random), 2);
            } else if (shape <= 1.30D && random.nextFloat() < 0.74F) {
                level.setBlock(pos, Blocks.MUD.defaultBlockState(), 2);
            } else if (shape <= 1.58D && random.nextFloat() < 0.48F) {
                level.setBlock(pos, random.nextBoolean()
                        ? Asterion.ANCIENT_MOSS.defaultBlockState()
                        : Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState(), 2);
            }
        }

        // If the basin naturally sits beside masonry, let a narrow two-block seep descend
        // directly into its edge. The water column is over the recessed basin, so it stays tidy.
        if (wallFeed != null && random.nextFloat() < 0.58F) {
            BlockPos lip = center.relative(wallFeed.direction, wallFeed.distance - 1);
            BlockPos backing = center.relative(wallFeed.direction, wallFeed.distance);
            if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(backing.above()))
                    && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(backing.above(2)))) {
                level.setBlock(lip.above(), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(lip.above(2), Blocks.WATER.defaultBlockState(), 2);
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

package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Crimson leaf litter which can cling to floors, ceilings, and maze walls. */
public final class TaintedPetalsFeature extends Feature<NoneFeatureConfiguration> {
    public TaintedPetalsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int minX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int minZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        int placed = 0;

        for (int patch = 0; patch < 5; patch++) {
            int x = minX + 2 + random.nextInt(12);
            int z = minZ + 2 + random.nextInt(12);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
            if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "tainted_foliage")) continue;

            int radius = 1 + random.nextInt(3);
            for (int attempt = 0; attempt < 7 + radius * 3; attempt++) {
                int dx = random.nextIntBetweenInclusive(-radius, radius);
                int dz = random.nextIntBetweenInclusive(-radius, radius);
                BlockPos nearbyFloor = OvergrowthFeatureSupport.findFloor(level,
                        floor.getX() + dx, floor.getZ() + dz);
                if (nearbyFloor == null || !OvergrowthFeatureSupport.enabled(
                        level, nearbyFloor, "tainted_foliage")) continue;
                placed += placeFace(level, nearbyFloor.above(), Direction.DOWN) ? 1 : 0;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int distance = 1; distance <= 8; distance++) {
                    int rise = 1 + random.nextInt(9);
                    BlockPos wall = floor.relative(direction, distance).above(rise);
                    BlockState support = level.getBlockState(wall);
                    if (!OvergrowthFeatureSupport.isMazeWall(support)
                            && !support.is(Asterion.TAINTED_LEAVES)) continue;
                    BlockPos surface = wall.relative(direction.getOpposite());
                    for (int spread = -1; spread <= 1; spread++) {
                        BlockPos petal = surface.above(spread);
                        if (random.nextFloat() < 0.72F)
                            placed += placeFace(level, petal, direction) ? 1 : 0;
                    }
                    break;
                }
            }
        }
        return placed > 0;
    }

    private static boolean placeFace(WorldGenLevel level, BlockPos pos, Direction supportDirection) {
        if (!OvergrowthFeatureSupport.canWrite(level, pos)) return false;
        BlockState oldState = level.getBlockState(pos);
        if (!oldState.isAir() && !oldState.is(Asterion.TAINTED_PETALS)) return false;
        BlockState state = Asterion.TAINTED_PETALS.getStateForPlacement(
                oldState, level, pos, supportDirection);
        if (state == null) return false;
        level.setBlock(pos, state, 2);
        return true;
    }

    /** Places the thin petal plane immediately over a marsh pool instead of using a leaf block. */
    static boolean placeOnWaterSurface(WorldGenLevel level, BlockPos pos) {
        if (!OvergrowthFeatureSupport.canWrite(level, pos) || !level.getBlockState(pos).isAir()
                || !level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)) return false;
        BlockState state = Asterion.TAINTED_PETALS.defaultBlockState()
                .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
        level.setBlock(pos, state, 2);
        return true;
    }
}

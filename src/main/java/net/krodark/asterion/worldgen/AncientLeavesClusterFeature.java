package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class AncientLeavesClusterFeature extends Feature<NoneFeatureConfiguration> {
    public AncientLeavesClusterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!OvergrowthFeatureSupport.enabled(level, origin, "leaf_clusters")) return false;
        int placed = 0;
        int clusters = 5 + random.nextInt(4);
        BlockState leaves = Asterion.ANCIENT_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        for (int cluster = 0; cluster < clusters; cluster++) {
            int centerX = origin.getX() + random.nextIntBetweenInclusive(-7, 7);
            int centerZ = origin.getZ() + random.nextIntBetweenInclusive(-7, 7);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, centerX, centerZ);
            if (floor == null) continue;
            int wallY = floor.getY() + 6 + random.nextInt(Math.max(1,
                    AsterionConfig.INSTANCE.wallHeight - 11));
            WallAttachment wall = findWall(level, new BlockPos(centerX, wallY, centerZ),
                    AsterionConfig.INSTANCE.cellSize);
            if (wall == null) continue;
            placed += placeWallClump(level, wall, leaves, random);
            if ((cluster & 1) == 0) placed += placeLeafTrail(level, wall, leaves, random);
        }
        return placed > 0;
    }

    private static WallAttachment findWall(WorldGenLevel level, BlockPos center, int maxDistance) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int distance = 1; distance <= maxDistance; distance++) {
            for (Direction towardWall : directions) {
                BlockPos wall = center.relative(towardWall, distance);
                Direction outward = towardWall.getOpposite();
                if (OvergrowthFeatureSupport.canWrite(level, wall.relative(outward))
                        && OvergrowthFeatureSupport.isMazeWall(level.getBlockState(wall)))
                    return new WallAttachment(wall, outward);
            }
        }
        return null;
    }

    private static int placeWallClump(WorldGenLevel level, WallAttachment wall,
                                      BlockState leaves, RandomSource random) {
        Direction tangent = wall.outward.getClockWise();
        int radiusAcross = 2 + random.nextInt(3);
        int radiusY = 2 + random.nextInt(3);
        int depth = 1 + random.nextInt(2);
        int placed = 0;
        for (int across = -radiusAcross; across <= radiusAcross; across++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int outward = 1; outward <= depth; outward++) {
                    double shape = across * across / (double)(radiusAcross * radiusAcross)
                            + dy * dy / (double)(radiusY * radiusY)
                            + (outward - 1) * (outward - 1) / (double)Math.max(1, depth * depth);
                    if (shape > 1.08D) continue;
                    BlockPos pos = wall.anchor.relative(tangent, across).above(dy)
                            .relative(wall.outward, outward);
                    if (!OvergrowthFeatureSupport.canWrite(level, pos)) continue;
                    BlockPos backing = pos.relative(wall.outward.getOpposite());
                    if (!OvergrowthFeatureSupport.enabled(level, pos, "leaf_clusters")
                            || !OvergrowthFeatureSupport.isOpen(level, pos)
                            || (outward == 1 && !OvergrowthFeatureSupport.isMazeWall(
                                    level.getBlockState(backing)))
                            || (outward > 1 && !level.getBlockState(backing).is(Asterion.ANCIENT_LEAVES)))
                        continue;
                    level.setBlock(pos, leaves, 2);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static int placeLeafTrail(WorldGenLevel level, WallAttachment wall,
                                      BlockState leaves, RandomSource random) {
        Direction tangent = wall.outward.getClockWise();
        int length = 9 + random.nextInt(9);
        double phase = random.nextDouble() * Math.PI * 2.0D;
        int previousAcross = 0;
        int placed = 0;
        for (int step = 0; step < length; step++) {
            int across = (int)Math.round(Math.sin(phase + step * 0.48D) * 2.1D);
            // Descend before moving sideways so leaves stay face-connected.
            BlockPos elbow = wall.anchor.relative(tangent, previousAcross).below(step)
                    .relative(wall.outward);
            placed += placeRootedLeaf(level, elbow, wall.outward, leaves);
            int direction = Integer.compare(across, previousAcross);
            for (int offset = previousAcross + direction;
                 direction != 0 && offset != across + direction; offset += direction) {
                BlockPos segment = wall.anchor.relative(tangent, offset).below(step)
                        .relative(wall.outward);
                placed += placeRootedLeaf(level, segment, wall.outward, leaves);
            }
            previousAcross = across;
        }
        return placed;
    }

    private static int placeRootedLeaf(WorldGenLevel level, BlockPos pos, Direction outward,
                                       BlockState leaves) {
        if (!OvergrowthFeatureSupport.canWrite(level, pos)
                || !OvergrowthFeatureSupport.enabled(level, pos, "leaf_clusters")
                || !OvergrowthFeatureSupport.isOpen(level, pos)
                || !OvergrowthFeatureSupport.isMazeWall(
                        level.getBlockState(pos.relative(outward.getOpposite())))) return 0;
        level.setBlock(pos, leaves, 2);
        return 1;
    }

    private record WallAttachment(BlockPos anchor, Direction outward) { }
}

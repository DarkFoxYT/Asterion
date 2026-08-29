package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Stable worldgen feature for clustered floor growth; intentionally does no chunk loading. */
public final class AncientMossPatchFeature extends Feature<NoneFeatureConfiguration> {
    public AncientMossPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int placed = 0;
        int patches = 2 + random.nextInt(3);
        for (int patch = 0; patch < patches; patch++) {
            int centerX = origin.getX() + random.nextIntBetweenInclusive(-7, 7);
            int centerZ = origin.getZ() + random.nextIntBetweenInclusive(-7, 7);
            int radius = 2 + random.nextInt(3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius || random.nextFloat() < 0.28F) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int expectedY = WorldGenerator.mazeFloorHeight(level.getSeed(), x, z);
                    BlockPos floor = findFloor(level, x, expectedY, z);
                    if (floor == null || !openForGrowth(level, floor.above())) continue;
                    long edge = (long)dx * dx + (long)dz * dz;
                    if (edge <= Math.max(1, radius * radius / 2) && random.nextFloat() < 0.58F)
                        level.setBlock(floor, Asterion.ANCIENT_MOSS.defaultBlockState(), 2);
                    BlockPos growth = floor.above();
                    if (random.nextFloat() < 0.54F)
                        level.setBlock(growth, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                    else if (random.nextFloat() < 0.18F)
                        level.setBlock(growth, Asterion.SHORT_GRASS.defaultBlockState(), 2);
                    placed++;
                }
            }
        }
        return placed > 0;
    }

    private static BlockPos findFloor(WorldGenLevel level, int x, int expectedY, int z) {
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos floor = new BlockPos(x, expectedY + dy, z);
            var state = level.getBlockState(floor);
            if ((state.is(Asterion.ANCIENT_STONE) || state.is(Asterion.ANCIENT_BRICKS)
                    || state.is(Asterion.ANCIENT_MOSS)) && openForGrowth(level, floor.above()))
                return floor;
        }
        return null;
    }

    private static boolean openForGrowth(WorldGenLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.above(2)).isAir();
    }
}

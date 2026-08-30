package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A small, native-material refuge that gives Overgrowth a quiet lived-in landmark. */
public final class OvergrowthRestSiteFeature extends Feature<NoneFeatureConfiguration> {
    public OvergrowthRestSiteFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int minX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int minZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        long siteRoll = mix(level.getSeed() ^ (long)minX * 0x9E3779B97F4A7C15L
                ^ (long)minZ * 0xD1B54A32D192ED03L);
        if (Math.floorMod(siteRoll, 4) != 0) return false;

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = minX + 3 + random.nextInt(10);
            int z = minZ + 3 + random.nextInt(10);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
            if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "rest_sites")
                    || !isClear(level, floor)) continue;
            build(level, floor, (siteRoll & 1L) == 0L ? Direction.NORTH : Direction.WEST);
            return true;
        }
        return false;
    }

    private static boolean isClear(WorldGenLevel level, BlockPos center) {
        int y = center.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floor = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                if (!OvergrowthFeatureSupport.isMazeFloor(level, floor)) return false;
                for (int rise = 1; rise <= 3; rise++)
                    if (!OvergrowthFeatureSupport.isOpen(level, floor.above(rise))) return false;
            }
        }
        return true;
    }

    private static void build(WorldGenLevel level, BlockPos center, Direction benchAxis) {
        // A restrained five-block mosaic identifies the refuge without carpeting paths.
        level.setBlock(center, Asterion.ANCIENT_MOSS.defaultBlockState(), 2);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            level.setBlock(center.relative(direction),
                    Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState(), 2);

        Direction side = benchAxis.getClockWise();
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos first = center.relative(benchAxis, 2).relative(side, offset).above();
            BlockPos second = center.relative(benchAxis.getOpposite(), 2).relative(side, offset).above();
            level.setBlock(first, Asterion.ANCIENT_PLANK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, benchAxis.getOpposite()), 2);
            level.setBlock(second, Asterion.ANCIENT_PLANK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, benchAxis), 2);
        }

        // Two segments produce one warm bulb while leaving an uncluttered ring around it.
        level.setBlock(center.above(), Asterion.LABYRINTH_VINE.defaultBlockState()
                .setValue(LabyrinthVineBlock.FACING, Direction.UP)
                .setValue(LabyrinthVineBlock.END, false), 2);
        level.setBlock(center.above(2), Asterion.LABYRINTH_VINE.defaultBlockState()
                .setValue(LabyrinthVineBlock.FACING, Direction.UP)
                .setValue(LabyrinthVineBlock.END, true), 2);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}

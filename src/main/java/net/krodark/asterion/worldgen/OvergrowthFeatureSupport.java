package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

final class OvergrowthFeatureSupport {
    private OvergrowthFeatureSupport() { }

    static boolean enabled(WorldGenLevel level, BlockPos pos, String feature) {
        return level.getLevel().dimension().equals(Asterion.ASTERION_LEVEL)
                && WorldGenerator.mazeBiomeHasFeature(pos.getX(), pos.getZ(), feature);
    }

    static BlockPos findFloor(WorldGenLevel level, int x, int z) {
        int expectedY = WorldGenerator.mazeFloorHeight(WorldGenerator.mazeTerrainSeed(), x, z);
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos floor = new BlockPos(x, expectedY + dy, z);
            if (isMazeFloor(level, floor) && level.getBlockState(floor.above()).isAir()) return floor;
        }
        return null;
    }

    static boolean isMazeFloor(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Asterion.ANCIENT_STONE) || state.is(Asterion.ANCIENT_BRICKS)
                || state.is(Asterion.ANCIENT_MOSS) || state.is(Asterion.MOSSY_ANCIENT_STONE)
                || state.is(Asterion.ANCIENT_MOSSY_BRICKS);
    }

    static boolean isMazeWall(BlockState state) {
        return state.is(Asterion.ANCIENT_STONE) || state.is(Asterion.ANCIENT_BRICKS)
                || state.is(Asterion.MOSSY_ANCIENT_STONE)
                || state.is(Asterion.ANCIENT_MOSSY_BRICKS)
                || state.is(Asterion.MAZESTEEL_BLOCK);
    }

    static boolean isOpen(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }
}

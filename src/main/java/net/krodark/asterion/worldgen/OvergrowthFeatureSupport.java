package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.mixin.WorldGenRegionAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;

final class OvergrowthFeatureSupport {
    private OvergrowthFeatureSupport() { }

    static boolean canWrite(WorldGenLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        if (level instanceof WorldGenRegion region) {
            int radius = ((WorldGenRegionAccessor) region).asterion$generatingStep().blockStateWriteRadius();
            if (!withinWriteRadius(region.getCenter(), pos, radius)) return false;
        }
        // ensureCanWrite logs rejected chunks, so check horizontal bounds first.
        return level.ensureCanWrite(pos);
    }

    static boolean withinWriteRadius(ChunkPos center, BlockPos pos, int radius) {
        return Math.abs((long) (pos.getX() >> 4) - center.x()) <= radius
                && Math.abs((long) (pos.getZ() >> 4) - center.z()) <= radius;
    }

    static boolean enabled(WorldGenLevel level, BlockPos pos, String feature) {
        return level.getLevel().dimension().equals(Asterion.ASTERION_LEVEL)
                && WorldGenerator.mazeBiomeHasFeature(terrainSeed(level), pos.getX(), pos.getZ(), feature);
    }

    static long terrainSeed(WorldGenLevel level) {
        return MazeChunkGenerator.terrainSeed(level.getLevel().getChunkSource().randomState());
    }

    static BlockPos findFloor(WorldGenLevel level, int x, int z) {
        int expectedY = WorldGenerator.mazeFloorHeight(terrainSeed(level), x, z);
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos floor = new BlockPos(x, expectedY + dy, z);
            if (isMazeFloor(level, floor) && isOpen(level, floor.above())) return floor;
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
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(Asterion.ANCIENT_MOSS_CARPET) || state.is(Asterion.SHORT_GRASS)
                || state.is(Asterion.TAINTED_PETALS);
    }
}

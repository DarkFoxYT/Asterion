package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A rotated walkway which is only built when two real, opposing wall faces are found. */
public final class OvergrowthBridgeFeature extends Feature<NoneFeatureConfiguration> {
    public OvergrowthBridgeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        AsterionConfig config = AsterionConfig.INSTANCE;
        int cell = config.cellSize;
        int minX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int minZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        int maxX = minX + 15, maxZ = minZ + 15;
        int firstCellX = Math.floorDiv(minX - cell / 2, cell);
        int lastCellX = Math.floorDiv(maxX - cell / 2, cell);
        int firstCellZ = Math.floorDiv(minZ - cell / 2, cell);
        int lastCellZ = Math.floorDiv(maxZ - cell / 2, cell);

        // Scan the actual maze-cell centers owned by this chunk. This avoids the old
        // random-origin mismatch that made valid bridges almost never receive a try.
        for (int gridX = firstCellX; gridX <= lastCellX; gridX++) {
            for (int gridZ = firstCellZ; gridZ <= lastCellZ; gridZ++) {
                int cellX = gridX * cell, cellZ = gridZ * cell;
                int centerX = cellX + cell / 2, centerZ = cellZ + cell / 2;
                if (centerX < minX || centerX > maxX || centerZ < minZ || centerZ > maxZ) continue;
                BlockPos floor = OvergrowthFeatureSupport.findFloor(level, centerX, centerZ);
                if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "bridges")) continue;
                long roll = mix(level.getSeed() ^ (long)cellX * 0x9E3779B97F4A7C15L
                        ^ (long)cellZ * 0xD1B54A32D192ED03L);
                if (Math.floorMod(roll, 2) != 0) continue;
                int[] heights = {Math.max(7, config.wallHeight * 5 / 12),
                        Math.max(10, config.wallHeight * 3 / 5)};
                for (int height : heights) {
                    BlockPos center = new BlockPos(centerX, floor.getY() + height, centerZ);
                    Span xSpan = findWallSpan(level, center, Direction.WEST, Direction.EAST, cell + 3);
                    Span zSpan = findWallSpan(level, center, Direction.NORTH, Direction.SOUTH, cell + 3);
                    Span span = (roll & 4L) == 0L ? firstValid(xSpan, zSpan) : firstValid(zSpan, xSpan);
                    if (span != null && buildBridge(level, span, roll) > 0) return true;
                }
            }
        }
        return false;
    }

    private static Span firstValid(Span preferred, Span fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static Span findWallSpan(WorldGenLevel level, BlockPos center,
                                     Direction negative, Direction positive, int maxDistance) {
        BlockPos first = findWall(level, center, negative, maxDistance);
        BlockPos second = findWall(level, center, positive, maxDistance);
        if (first == null || second == null) return null;
        int distance = Math.abs(second.getX() - first.getX()) + Math.abs(second.getZ() - first.getZ());
        if (distance < 5 || distance > maxDistance + 3) return null;
        return new Span(first, second, positive, distance);
    }

    private static BlockPos findWall(WorldGenLevel level, BlockPos center, Direction direction, int maxDistance) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos candidate = center.relative(direction, distance);
            if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(candidate))) return candidate;
        }
        return null;
    }

    private static int buildBridge(WorldGenLevel level, Span span, long seed) {
        int placed = 0;
        Direction side = span.direction.getClockWise();
        BlockState leaves = Asterion.ANCIENT_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, true);
        for (int step = 0; step <= span.distance; step++) {
            BlockPos centerDeck = span.first.relative(span.direction, step);
            double foliage = smoothNoise1D(seed ^ 0xA24BAED4963EE407L, step / 3.4D);
            for (int width = -1; width <= 1; width++) {
                BlockPos deck = centerDeck.relative(side, width);
                boolean anchor = step == 0 || step == span.distance;
                if (!anchor && !OvergrowthFeatureSupport.isOpen(level, deck)) continue;
                long detail = mix(seed ^ (long)step * 0x9E3779B97F4A7C15L ^ width * 31L);
                BlockState state = anchor || width == 0 || Math.floorMod(detail, 6) == 0
                        ? Asterion.ANCIENT_PLANKS.defaultBlockState()
                        : Asterion.ANCIENT_PLANK_SLAB.defaultBlockState()
                                .setValue(SlabBlock.TYPE, SlabType.TOP);
                level.setBlock(deck, state, 2);
                placed++;

                if (!anchor && Math.floorMod(detail >>> 11, 11) == 0
                        && OvergrowthFeatureSupport.isOpen(level, deck.above()))
                    level.setBlock(deck.above(), Asterion.ANCIENT_MOSS_CARPET.defaultBlockState(), 2);

                // Foliage grows from the underside edges in broad runs, not isolated leaf pixels.
                if (!anchor && width != 0 && foliage > 0.43D
                        && OvergrowthFeatureSupport.isOpen(level, deck.below())) {
                    level.setBlock(deck.below(), leaves, 2);
                    if (foliage > 0.68D && OvergrowthFeatureSupport.isOpen(level, deck.below(2)))
                        level.setBlock(deck.below(2), leaves, 2);
                }
            }
        }

        // Deep wood sockets plus rotated brackets visibly lock both ends into masonry.
        placeAnchor(level, span.first, span.direction);
        placeAnchor(level, span.second, span.direction.getOpposite());
        return placed;
    }

    private static void placeAnchor(WorldGenLevel level, BlockPos wall, Direction towardCenter) {
        BlockPos deepSocket = wall.relative(towardCenter.getOpposite());
        if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(deepSocket)))
            level.setBlock(deepSocket, Asterion.ANCIENT_PLANKS.defaultBlockState(), 2);
        level.setBlock(wall, Asterion.ANCIENT_PLANKS.defaultBlockState(), 2);
        placeBracket(level, wall.relative(towardCenter), towardCenter);
        BlockPos lowerSocket = wall.below();
        if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(lowerSocket)))
            level.setBlock(lowerSocket, Asterion.ANCIENT_PLANKS.defaultBlockState(), 2);
    }

    private static void placeBracket(WorldGenLevel level, BlockPos deckEnd, Direction towardCenter) {
        BlockPos bracket = deckEnd.below();
        if (!OvergrowthFeatureSupport.isOpen(level, bracket)) return;
        level.setBlock(bracket, Asterion.ANCIENT_PLANK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, towardCenter), 2);
    }

    private static double smoothNoise1D(long seed, double coordinate) {
        int left = (int)Math.floor(coordinate);
        double fraction = coordinate - left;
        double smooth = fraction * fraction * (3.0D - 2.0D * fraction);
        double a = (mix(seed ^ (long)left * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
        double b = (mix(seed ^ (long)(left + 1) * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
        return a + (b - a) * smooth;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record Span(BlockPos first, BlockPos second, Direction direction, int distance) { }
}

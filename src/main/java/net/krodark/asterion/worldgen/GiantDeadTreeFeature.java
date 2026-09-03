package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.ShatteredDeadWoodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Monumental dead mangroves which physically interrupt and pierce Overgrowth walls. */
public final class GiantDeadTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public GiantDeadTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Repairs the exact four-layer opening made by the pre-fix centered road carver. */
    public static void repairLegacyTrunkGaps(ServerLevel level, LevelChunk chunk, boolean legacyChunk) {
        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX() + 2, 1,
                chunk.getPos().getMinBlockZ());
        if (chunk.getBlockState(marker).is(net.minecraft.world.level.block.Blocks.BEDROCK)) return;
        if (legacyChunk) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int minX = chunk.getPos().getMinBlockX(), maxX = chunk.getPos().getMaxBlockX();
            int minZ = chunk.getPos().getMinBlockZ(), maxZ = chunk.getPos().getMaxBlockZ();
            long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                int floorY = net.krodark.asterion.WorldGenerator.mazeFloorHeight(seed, x, z);
                if (!isTreeWood(chunk.getBlockState(cursor.set(x, floorY, z)))
                        || !chunk.getBlockState(cursor.set(x, floorY + 1, z)).isAir()) continue;
                BlockPos upper = nearestUpperTrunk(chunk, x, floorY + 5, z, minX, maxX, minZ, maxZ);
                if (upper == null) continue;
                for (int step = 1; step <= 4; step++) {
                    double progress = step / 5.0D;
                    int repairX = Mth.floor(x + (upper.getX() - x) * progress + .5D);
                    int repairZ = Mth.floor(z + (upper.getZ() - z) * progress + .5D);
                    cursor.set(repairX, floorY + step, repairZ);
                    if (chunk.getBlockState(cursor).isAir())
                        chunk.setBlockState(cursor, Asterion.DEAD_WOOD.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 0);
                }
            }
        }
        chunk.setBlockState(marker, net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 0);
    }

    private static BlockPos nearestUpperTrunk(LevelChunk chunk, int x, int y, int z,
                                               int minX, int maxX, int minZ, int maxZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int testX = x + dx, testZ = z + dz;
            if (testX < minX || testX > maxX || testZ < minZ || testZ > maxZ) continue;
            if (!isTreeWood(chunk.getBlockState(cursor.set(testX, y, testZ)))) continue;
            int distance = dx * dx + dz * dz;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = cursor.immutable();
            }
        }
        return closest;
    }

    private static boolean isTreeWood(BlockState state) {
        return state.is(Asterion.DEAD_WOOD) || state.is(Asterion.SHATTERED_DEAD_WOOD);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int chunkX = Math.floorDiv(context.origin().getX(), 16);
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16);
        int regionX = Math.floorDiv(chunkX, 4);
        int regionZ = Math.floorDiv(chunkZ, 4);
        long regionRoll = mix(level.getSeed() ^ (long)regionX * 0x9E3779B97F4A7C15L
                ^ (long)regionZ * 0xD1B54A32D192ED03L);
        int owner = (int)Math.floorMod(regionRoll, 16L);
        if (Math.floorMod(chunkX, 4) != owner % 4 || Math.floorMod(chunkZ, 4) != owner / 4)
            return false;

        int minX = chunkX * 16, minZ = chunkZ * 16;
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = minX + 3 + random.nextInt(10);
            int z = minZ + 3 + random.nextInt(10);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
            if (floor == null) continue;
            boolean bonsai = OvergrowthFeatureSupport.enabled(level, floor, "crimson_bonsai");
            if (!bonsai && !OvergrowthFeatureSupport.enabled(level, floor, "giant_dead_trees")) continue;
            WallAttachment wall = findWall(level, floor.above(4),
                    AsterionConfig.INSTANCE.cellSize);
            if (wall == null) continue;
            grow(level, floor, wall, random, regionRoll,
                    new TreeBounds(minX, minX + 15, minZ, minZ + 15), bonsai);
            return true;
        }
        return false;
    }

    private static void grow(WorldGenLevel level, BlockPos base, WallAttachment wall,
                             RandomSource random, long seed, TreeBounds bounds, boolean bonsai) {
        // Crimson trees should frame the enormous ring walls, not compete with them.
        int diameter = bonsai ? 1 + random.nextInt(2) : 3 + random.nextInt(4);
        int wallHeight = AsterionConfig.INSTANCE.wallHeight;
        int height = bonsai ? 8 + random.nextInt(6)
                : Math.max(22, wallHeight - 3 + random.nextInt(9));
        Direction lean = wall.towardWall;
        Direction tangent = lean.getClockWise();
        int leanDistance = bonsai ? 1 + random.nextInt(2) : 5 + random.nextInt(7);

        BlockPos[] spine = new BlockPos[height + 1];
        for (int rise = 0; rise <= height; rise++) {
            double progress = rise / (double)height;
            int forward = Mth.floor(Math.pow(progress, 1.42D) * leanDistance + 0.5D);
            int sway = Mth.floor(Math.sin(progress * Math.PI * 2.2D
                    + ((seed >>> 12) & 255L) * 0.017D) * (0.7D + progress * 1.3D));
            BlockPos center = bounds.clamp(base.above(rise)
                    .relative(lean, forward).relative(tangent, sway), 3);
            spine[rise] = center;
            int layerDiameter = progress < 0.72D ? diameter
                    : Math.max(bonsai ? 1 : 2,
                            (int)Math.ceil(diameter * (1.0D - (progress - 0.72D) * 1.8D)));
            placeVerticalDisk(level, center, layerDiameter);
        }

        growButtressRoots(level, base, diameter, random, seed, bounds, bonsai);
        growCrown(level, spine, diameter, random, seed, bounds, bonsai);
        BlockPos apex = spine[height].above();
        setShattered(level, apex, Direction.UP);
        if (bonsai) {
            int apexRadius = 4 + random.nextInt(2);
            placeTaintedCanopy(level, bounds.clamp(spine[height - 2], apexRadius),
                    apexRadius, random);
        }

        // Route the walkable lane around the trunk's open side. The old centered lane
        // cut rises 1-4 out of every trunk and left the crown visibly floating.
        carveRoad(level, base, tangent, diameter + 5, diameter);
    }

    private static void growButtressRoots(WorldGenLevel level, BlockPos base, int diameter,
                                          RandomSource random, long seed, TreeBounds bounds,
                                          boolean bonsai) {
        int roots = bonsai ? 2 + random.nextInt(3) : 7 + random.nextInt(4);
        for (int root = 0; root < roots; root++) {
            double angle = Math.PI * 2.0D * root / roots
                    + signedUnit(mix(seed ^ root * 0xA24BAED4963EE407L)) * 0.34D;
            int length = bonsai ? 2 + random.nextInt(4) : 9 + random.nextInt(8);
            BlockPos end = bounds.clamp(base.offset(Mth.floor(Math.cos(angle) * length),
                    -1 - random.nextInt(3), Mth.floor(Math.sin(angle) * length)), 1);
            placeTaperedLimb(level, base.above(), end,
                    bonsai ? 1 : Math.max(2, Math.min(4, diameter - 1)), false);
        }
    }

    private static void growCrown(WorldGenLevel level, BlockPos[] spine, int diameter,
                                  RandomSource random, long seed, TreeBounds bounds, boolean bonsai) {
        int branches = (bonsai ? 3 : 7) + random.nextInt(bonsai ? 3 : 5);
        for (int branch = 0; branch < branches; branch++) {
            int startIndex = spine.length * (48 + random.nextInt(39)) / 100;
            BlockPos start = spine[Math.min(spine.length - 1, startIndex)];
            double angle = Math.PI * 2.0D * branch / branches
                    + signedUnit(mix(seed ^ branch * 0x8CB92BA72F3D8DD7L)) * 0.46D;
            int reach = bonsai ? 3 + random.nextInt(4) : 11 + random.nextInt(10);
            int lift = bonsai ? 1 + random.nextInt(4) - (branch % 4 == 0 ? 1 : 0)
                    : 3 + random.nextInt(9) - (branch % 4 == 0 ? 5 : 0);
            int canopyRadius = bonsai ? 3 + random.nextInt(3) : 0;
            BlockPos end = bounds.clamp(start.offset(Mth.floor(Math.cos(angle) * reach), lift,
                    Mth.floor(Math.sin(angle) * reach)), bonsai ? canopyRadius + 1 : 1);
            LimbTip tip = placeTaperedLimb(level, start, end,
                    bonsai ? 1 : Math.max(2, Math.min(4, diameter - 1)), true);
            setShattered(level, tip.position, tip.direction);
            if (bonsai) placeTaintedCanopy(level, bounds.clamp(tip.position, canopyRadius),
                    canopyRadius, random);

            // Large branches fork once, giving a mangrove silhouette without leaf blobs.
            if ((branch & 1) == 0) {
                double forkAngle = angle + (branch % 4 == 0 ? 0.58D : -0.58D);
                int forkReach = bonsai ? 2 + random.nextInt(3) : 6 + random.nextInt(7);
                int forkCanopyRadius = bonsai ? 3 + random.nextInt(2) : 0;
                BlockPos forkEnd = bounds.clamp(end.offset(Mth.floor(Math.cos(forkAngle) * forkReach),
                        bonsai ? 1 + random.nextInt(3) : 2 + random.nextInt(5),
                        Mth.floor(Math.sin(forkAngle) * forkReach)),
                        bonsai ? forkCanopyRadius + 1 : 1);
                LimbTip forkTip = placeTaperedLimb(level, tip.position, forkEnd,
                        bonsai ? 1 : 2, true);
                setShattered(level, forkTip.position, forkTip.direction);
                if (bonsai && random.nextFloat() < 0.72F)
                    placeTaintedCanopy(level, bounds.clamp(forkTip.position, forkCanopyRadius),
                            forkCanopyRadius, random);
            }
        }
    }

    private static void placeTaintedCanopy(WorldGenLevel level, BlockPos center, int radius,
                                            RandomSource random) {
        int verticalRadius = Math.max(2, radius / 2);
        for (int dx = -radius; dx <= radius; dx++) {
            // A bonsai crown begins on one clean horizontal plane and only domes upward.
            // This removes the round, hanging underside produced by a full ellipsoid.
            for (int dy = 0; dy <= verticalRadius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!OvergrowthFeatureSupport.canWrite(level, pos)) continue;
                    double shape = dx * dx / (double)(radius * radius)
                            + dz * dz / (double)(radius * radius)
                            + dy * dy / (double)(verticalRadius * verticalRadius);
                    double fray = random.nextDouble() * 0.12D;
                    if (shape > 1.0D - fray) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !state.is(Asterion.ANCIENT_LEAVES)
                            && !state.is(Asterion.TAINTED_LEAVES)) continue;
                    level.setBlock(pos, Asterion.TAINTED_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
                }
            }
        }
        // Fruit only occupies exposed points on the flat underside. Keeping this pass
        // separate guarantees every bloom is visibly attached to a leaf and never floats.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (random.nextFloat() >= 0.006F) continue;
                BlockPos leaf = center.offset(dx, 0, dz);
                BlockPos fruit = leaf.below();
                if (!OvergrowthFeatureSupport.canWrite(level, fruit) || !level.getBlockState(leaf).is(Asterion.TAINTED_LEAVES)
                        || !level.getBlockState(fruit).isAir()) continue;
                level.setBlock(fruit, Asterion.PASSION_BLOOM.defaultBlockState(), 2);
            }
        }
    }

    private static LimbTip placeTaperedLimb(WorldGenLevel level, BlockPos start,
                                            BlockPos end, int startDiameter,
                                            boolean keepTipOpen) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        Direction.Axis axis = dominantAxis(dx, dy, dz);
        Direction tipDirection = dominantDirection(dx, dy, dz);
        BlockPos lastCenter = start;
        for (int step = 0; step <= steps; step++) {
            if (keepTipOpen && step == steps) break;
            double progress = step / (double)steps;
            BlockPos center = new BlockPos(
                    Mth.floor(start.getX() + dx * progress + 0.5D),
                    Mth.floor(start.getY() + dy * progress + 0.5D),
                    Mth.floor(start.getZ() + dz * progress + 0.5D));
            int diameter = Math.max(1, (int)Math.ceil(startDiameter * (1.0D - progress * 0.68D)));
            placeWoodBall(level, center, diameter, axis);
            lastCenter = center;
        }
        return new LimbTip(lastCenter.relative(tipDirection), tipDirection);
    }

    private static void placeVerticalDisk(WorldGenLevel level, BlockPos center, int diameter) {
        int min = -diameter / 2;
        int max = (diameter - 1) / 2;
        double offset = (min + max) * 0.5D;
        double radius = diameter * 0.52D;
        for (int dx = min; dx <= max; dx++) {
            for (int dz = min; dz <= max; dz++) {
                double ox = dx - offset, oz = dz - offset;
                if (ox * ox + oz * oz > radius * radius) continue;
                setWood(level, center.offset(dx, 0, dz), Direction.Axis.Y);
            }
        }
    }

    private static void placeWoodBall(WorldGenLevel level, BlockPos center, int diameter,
                                      Direction.Axis axis) {
        int min = -diameter / 2;
        int max = (diameter - 1) / 2;
        double offset = (min + max) * 0.5D;
        double radiusSquared = Math.pow(diameter * 0.56D, 2.0D);
        for (int dx = min; dx <= max; dx++) {
            for (int dy = min; dy <= max; dy++) {
                for (int dz = min; dz <= max; dz++) {
                    double ox = dx - offset, oy = dy - offset, oz = dz - offset;
                    if (ox * ox + oy * oy + oz * oz > radiusSquared) continue;
                    setWood(level, center.offset(dx, dy, dz), axis);
                }
            }
        }
    }

    private static void carveRoad(WorldGenLevel level, BlockPos base, Direction road,
                                  int length, int trunkDiameter) {
        Direction side = road.getClockWise();
        int openSideOffset = Math.max(2, (trunkDiameter + 1) / 2 + 1);
        for (int along = -length / 2; along <= length / 2; along++) {
            for (int across = openSideOffset; across <= openSideOffset + 2; across++) {
                for (int rise = 1; rise <= 4; rise++) {
                    BlockPos pos = base.relative(road, along).relative(side, across).above(rise);
                    if (!OvergrowthFeatureSupport.canWrite(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Asterion.DEAD_WOOD) || state.is(Asterion.SHATTERED_DEAD_WOOD))
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void setWood(WorldGenLevel level, BlockPos pos, Direction.Axis axis) {
        if (!OvergrowthFeatureSupport.canWrite(level, pos) || !canReplace(level, pos)) return;
        level.setBlock(pos, Asterion.DEAD_WOOD.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, axis), 2);
    }

    private static void setShattered(WorldGenLevel level, BlockPos pos, Direction facing) {
        if (!OvergrowthFeatureSupport.canWrite(level, pos)
                || (!canReplace(level, pos) && !level.getBlockState(pos).is(Asterion.DEAD_WOOD))) return;
        level.setBlock(pos, Asterion.SHATTERED_DEAD_WOOD.defaultBlockState()
                .setValue(ShatteredDeadWoodBlock.FACING, facing), 2);
    }

    private static boolean canReplace(WorldGenLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) != null) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || OvergrowthFeatureSupport.isMazeWall(state)
                || OvergrowthFeatureSupport.isMazeFloor(level, pos)
                || state.is(Asterion.ANCIENT_LEAVES) || state.is(Asterion.ANCIENT_MOSS_CARPET)
                || state.is(Asterion.SHORT_GRASS) || state.is(Asterion.LABYRINTH_VINE)
                || state.is(Asterion.DEAD_WOOD) || state.is(Asterion.SHATTERED_DEAD_WOOD);
    }

    private static WallAttachment findWall(WorldGenLevel level, BlockPos origin, int distance) {
        for (int step = 1; step <= distance; step++) {
            for (Direction direction : HORIZONTAL) {
                BlockPos wall = origin.relative(direction, step);
                if (OvergrowthFeatureSupport.isMazeWall(level.getBlockState(wall)))
                    return new WallAttachment(wall, direction);
            }
        }
        return null;
    }

    private static Direction.Axis dominantAxis(int x, int y, int z) {
        int ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ay >= ax && ay >= az) return Direction.Axis.Y;
        return ax >= az ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static Direction dominantDirection(int x, int y, int z) {
        Direction.Axis axis = dominantAxis(x, y, z);
        int component = axis == Direction.Axis.X ? x : axis == Direction.Axis.Y ? y : z;
        return Direction.fromAxisAndDirection(axis, component >= 0
                ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private static double signedUnit(long value) {
        return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record WallAttachment(BlockPos wall, Direction towardWall) { }
    private record LimbTip(BlockPos position, Direction direction) { }
    private record TreeBounds(int minX, int maxX, int minZ, int maxZ) {
        private BlockPos clamp(BlockPos pos, int margin) {
            return new BlockPos(Mth.clamp(pos.getX(), minX + margin, maxX - margin),
                    pos.getY(), Mth.clamp(pos.getZ(), minZ + margin, maxZ - margin));
        }
    }
}

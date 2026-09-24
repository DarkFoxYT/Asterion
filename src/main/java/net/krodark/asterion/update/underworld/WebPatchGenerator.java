package net.krodark.asterion.update.underworld;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
/** Builds webs directly from loaded cave surfaces. It creates no blocks, block entities or entities. */
public final class WebPatchGenerator {
    /** Dense cells make Limbo feel filled with individual, traversable silk strands. */
    public static final int CELL_SIZE = 2;
    private static final Map<Level, Map<Integer, Cached>> CACHE = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Cached>> CAVE_CACHE = new WeakHashMap<>();
    private record Cached(WebPatch patch, long expires) { }
    private WebPatchGenerator() { }
    public static List<WebPatch> around(Level level, Vec3 center, int cells) {
        List<WebPatch> result = new ArrayList<>(); int middle = Math.floorDiv((int)Math.floor(center.z), CELL_SIZE);
        for (int cell = middle - cells; cell <= middle + cells; cell++) {
            Map<Integer, Cached> cache = CACHE.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
            Cached known = cache.get(cell); long now = level.getGameTime();
            if (known == null || known.expires < now) {
                known = new Cached(patch(level, 0x4C494D424F5F5745L, cell), now + 100);
                cache.put(cell, known);
                if (cache.size() > 160) cache.entrySet().removeIf(e -> e.getValue().expires < now);
            }
            WebPatch patch = known.patch;
            if (patch != null && patch.anchors().getFirst().distanceToSqr(center) < 52 * 52) result.add(patch);
        }
        double route = UnderworldTerrain.riverCenter(center.z) - 15;
        if (center.z < -42 && Math.abs(center.x - route) > 18) {
            int gx = Math.floorDiv((int)Math.floor(center.x), 8);
            int gz = Math.floorDiv((int)Math.floor(center.z), 8);
            int radius = cells >= 16 ? 3 : 2;
            Map<Long, Cached> caveCache = CAVE_CACHE.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
            long now = level.getGameTime();
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int cellX = gx + dx, cellZ = gz + dz;
                long key = net.minecraft.world.level.ChunkPos.pack(cellX, cellZ);
                Cached known = caveCache.get(key);
                if (known == null || known.expires < now) {
                    long seed = mix(0x5B1DE3L ^ key);
                    known = new Cached(patchAt(level, seed, cellX * 8 + 4, cellZ * 8 + 4), now + 100);
                    caveCache.put(key, known);
                }
                if (known.patch != null && known.patch.anchors().getFirst().distanceToSqr(center) < 52 * 52)
                    result.add(known.patch);
            }
            if (caveCache.size() > 320) caveCache.entrySet().removeIf(e -> e.getValue().expires < now);
        }
        return result;
    }
    private static WebPatch patch(Level level, long worldSeed, int cellZ) {
        long seed = mix(worldSeed ^ cellZ * 0x9E3779B97F4A7C15L);
        int z = cellZ * CELL_SIZE + 1 + (int)Math.floorMod(seed >>> 8, CELL_SIZE - 1);
        if (z < UnderworldTerrain.START_Z + 12 || z >= -42) return null;
        double path = UnderworldTerrain.riverCenter(z) - 15; int side = (seed & 4) == 0 ? -1 : 1;
        double chamberX = UnderworldTerrain.chamberWebX(z);
        double webX = Double.isNaN(chamberX) || (cellZ & 3) == 0
                ? path + side * (3 + Math.floorMod(seed >>> 12, 12)) : chamberX;
        return patchAt(level, seed, (int)Math.round(webX), z);
    }
    private static WebPatch patchAt(Level level, long seed, int x, int z) {
        // Keep the entire ferry landing and boarding approach clear of silk, including
        // strands whose anchor search would otherwise reach in from a nearby cell.
        if (z >= -42) return null;
        BlockPos center = new BlockPos(x,
                UnderworldTerrain.WATER_Y - 18 + (int)Math.floorMod(seed >>> 17, 78), z);
        if (!level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) return null;
        if (!level.getBlockState(center).isAir()) {
            BlockPos found = null;
            for (int step = 1; step <= 24 && found == null; step++) {
                BlockPos up = center.above(step), down = center.below(step);
                if (level.getBlockState(up).isAir()) found = up;
                else if (level.getBlockState(down).isAir()) found = down;
            }
            if (found == null) return null;
            center = found;
        }
        Direction preferred = (seed & 8) == 0 ? Direction.EAST : Direction.SOUTH;
        int reach = Math.abs(x - (UnderworldTerrain.riverCenter(z) - 15)) > 18 ? 13 : 8;
        Pair pair = findGap(level, center, preferred, reach);
        if (pair == null) pair = findGap(level, center, preferred == Direction.EAST ? Direction.SOUTH : Direction.EAST, reach);
        if (pair == null) return singleAnchor(level, center, seed, reach);
        List<Vec3> anchors = new ArrayList<>(); List<Vec3> normals = new ArrayList<>();
        anchors.add(face(pair.a, pair.axis, seed, 0));
        anchors.add(face(pair.b, pair.axis.getOpposite(), seed, 1));
        normals.add(pair.axis.getUnitVec3()); normals.add(pair.axis.getOpposite().getUnitVec3());
        // One tensioned span per patch. The former complete graph made X/+ webs
        // and multiplied both physics links and draw calls across every gap.
        return new WebPatch(mix(seed ^ pair.a.asLong() ^ Long.rotateLeft(pair.b.asLong(), 23)),
                List.copyOf(anchors), List.copyOf(normals), List.of(new WebPatch.Edge(0, 1)));
    }
    private static WebPatch singleAnchor(Level level, BlockPos center, long seed, int reach) {
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
        for (int i = 0; i < directions.length; i++) {
            Direction outward = directions[(i + (int)(seed & 7)) % directions.length];
            BlockPos anchorBlock = findAnchor(level, center, outward, reach);
            if (anchorBlock == null) continue;
            Direction inward = outward.getOpposite();
            Vec3 anchor = face(anchorBlock, inward, seed, 0);
            int length = Math.min(5, Math.max(2, anchorBlock.distManhattan(center) - 1));
            Vec3 free = anchor.add(inward.getUnitVec3().scale(length)).add(0, -.35, 0);
            if (!level.getBlockState(BlockPos.containing(free)).isAir()) continue;
            return new WebPatch(mix(seed ^ anchorBlock.asLong() ^ 0x51A61EL),
                    List.of(anchor, free), List.of(inward.getUnitVec3(), Vec3.ZERO),
                    List.of(new WebPatch.Edge(0, 1)));
        }
        return null;
    }
    private static Vec3 face(BlockPos block, Direction inward, long seed, int endpoint) {
        long random = mix(seed ^ block.asLong() ^ endpoint * 0xD1B54A32D192ED03L);
        double first = ((random >>> 11) * 0x1.0p-53 - .5) * .86;
        double second = ((mix(random) >>> 11) * 0x1.0p-53 - .5) * .86;
        Direction.Axis axis = inward.getAxis();
        Vec3 tangentA = axis == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangentB = axis == Direction.Axis.Z ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return Vec3.atCenterOf(block).add(inward.getUnitVec3().scale(.501))
                .add(tangentA.scale(first)).add(tangentB.scale(second));
    }
    private static Pair findGap(Level level, BlockPos center, Direction axis, int reach) {
        Direction lateral = axis == Direction.EAST ? Direction.SOUTH : Direction.EAST;
        for (int dy = 3; dy >= -3; dy--) for (int slide = -4; slide <= 4; slide++) {
            BlockPos origin = center.above(dy).relative(lateral, slide);
            if (!level.getChunkSource().hasChunk(origin.getX() >> 4, origin.getZ() >> 4)
                    || !level.getBlockState(origin).isAir()) continue;
            BlockPos a = findAnchor(level, origin, axis.getOpposite(), reach), b = findAnchor(level, origin, axis, reach);
            if (a != null && b != null && a.distManhattan(b) >= 3) return new Pair(a, b, axis);
        } return null;
    }
    private static BlockPos findAnchor(Level level, BlockPos origin, Direction direction, int reach) {
        for (int d = 1; d <= reach; d++) { BlockPos pos = origin.relative(direction, d);
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
            if (level.getBlockState(pos).isSolidRender()) return pos; if (!level.getBlockState(pos).isAir()) return null; }
        return null;
    }
    private record Pair(BlockPos a, BlockPos b, Direction axis) { }
    static long mix(long z) { z = (z ^ z >>> 30) * 0xbf58476d1ce4e5b9L; z = (z ^ z >>> 27) * 0x94d049bb133111ebL; return z ^ z >>> 31; }
}

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
    public static final int CELL_SIZE = 3;
    private static final Map<Level, Map<Integer, Cached>> CACHE = new WeakHashMap<>();
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
        return result;
    }
    private static WebPatch patch(Level level, long worldSeed, int cellZ) {
        long seed = mix(worldSeed ^ cellZ * 0x9E3779B97F4A7C15L);
        int z = cellZ * CELL_SIZE + 1 + (int)Math.floorMod(seed >>> 8, CELL_SIZE - 1);
        if (z < UnderworldTerrain.START_Z + 12 || z > UnderworldTerrain.END_Z - 24) return null;
        double path = UnderworldTerrain.riverCenter(z) - 15; int side = (seed & 4) == 0 ? -1 : 1;
        BlockPos center = new BlockPos((int)Math.round(path + side * (9 + Math.floorMod(seed >>> 12, 7))),
                UnderworldTerrain.WATER_Y + 3 + (int)Math.floorMod(seed >>> 17, 7), z);
        if (!level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) return null;
        Direction preferred = (seed & 8) == 0 ? Direction.EAST : Direction.SOUTH;
        Pair pair = findGap(level, center, preferred);
        if (pair == null) pair = findGap(level, center, preferred == Direction.EAST ? Direction.SOUTH : Direction.EAST);
        if (pair == null) return null;
        List<Vec3> anchors = new ArrayList<>(); List<Vec3> normals = new ArrayList<>();
        anchors.add(face(pair.a, pair.axis, seed, 0));
        anchors.add(face(pair.b, pair.axis.getOpposite(), seed, 1));
        normals.add(pair.axis.getUnitVec3()); normals.add(pair.axis.getOpposite().getUnitVec3());
        if ((seed & 16) != 0) {
            BlockPos mid = BlockPos.containing(anchors.get(0).lerp(anchors.get(1), .5));
            Direction vertical = (seed & 32) == 0 ? Direction.UP : Direction.DOWN;
            BlockPos third = findAnchor(level, mid, vertical, 6);
            if (third != null) { anchors.add(face(third, vertical.getOpposite(), seed, 2)); normals.add(vertical.getOpposite().getUnitVec3()); }
        }
        List<WebPatch.Edge> edges = anchors.size() == 3
                ? List.of(new WebPatch.Edge(0, 1), new WebPatch.Edge(1, 2), new WebPatch.Edge(2, 0))
                : List.of(new WebPatch.Edge(0, 1));
        return new WebPatch(mix(seed ^ pair.a.asLong() ^ Long.rotateLeft(pair.b.asLong(), 23)), List.copyOf(anchors), List.copyOf(normals), edges);
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
    private static Pair findGap(Level level, BlockPos center, Direction axis) {
        Direction lateral = axis == Direction.EAST ? Direction.SOUTH : Direction.EAST;
        for (int dy = 2; dy >= -2; dy--) for (int slide = -3; slide <= 3; slide++) {
            BlockPos origin = center.above(dy).relative(lateral, slide); if (!level.getBlockState(origin).isAir()) continue;
            BlockPos a = findAnchor(level, origin, axis.getOpposite(), 6), b = findAnchor(level, origin, axis, 6);
            if (a != null && b != null && a.distManhattan(b) >= 3) return new Pair(a, b, axis);
        } return null;
    }
    private static BlockPos findAnchor(Level level, BlockPos origin, Direction direction, int reach) {
        for (int d = 1; d <= reach; d++) { BlockPos pos = origin.relative(direction, d);
            if (level.getBlockState(pos).isSolidRender()) return pos; if (!level.getBlockState(pos).isAir()) return null; }
        return null;
    }
    private record Pair(BlockPos a, BlockPos b, Direction axis) { }
    static long mix(long z) { z = (z ^ z >>> 30) * 0xbf58476d1ce4e5b9L; z = (z ^ z >>> 27) * 0x94d049bb133111ebL; return z ^ z >>> 31; }
}

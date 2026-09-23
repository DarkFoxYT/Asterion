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
        double chamberX = UnderworldTerrain.chamberWebX(z);
        double webX = Double.isNaN(chamberX) || (cellZ & 3) == 0
                ? path + side * (3 + Math.floorMod(seed >>> 12, 12)) : chamberX;
        BlockPos center = new BlockPos((int)Math.round(webX),
                UnderworldTerrain.WATER_Y + 3 + (int)Math.floorMod(seed >>> 17, 7), z);
        if (!level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) return null;
        Direction preferred = (seed & 8) == 0 ? Direction.EAST : Direction.SOUTH;
        int reach = Double.isNaN(chamberX) ? 8 : 13;
        Pair pair = findGap(level, center, preferred, reach);
        if (pair == null) pair = findGap(level, center, preferred == Direction.EAST ? Direction.SOUTH : Direction.EAST, reach);
        if (pair == null) return null;
        List<Vec3> anchors = new ArrayList<>(); List<Vec3> normals = new ArrayList<>();
        anchors.add(face(pair.a, pair.axis, seed, 0));
        anchors.add(face(pair.b, pair.axis.getOpposite(), seed, 1));
        normals.add(pair.axis.getUnitVec3()); normals.add(pair.axis.getOpposite().getUnitVec3());
        BlockPos mid = BlockPos.containing(anchors.get(0).lerp(anchors.get(1), .5));
        // Prefer a true floor-to-ceiling span through the open gap, not just side-wall ropes.
        BlockPos floor = findAnchor(level, mid, Direction.DOWN, 9);
        BlockPos ceiling = findAnchor(level, mid, Direction.UP, 9);
        if (floor != null && ceiling != null && ceiling.getY() - floor.getY() >= 4) {
            anchors.add(face(floor, Direction.UP, seed, anchors.size()));
            normals.add(Direction.UP.getUnitVec3());
            anchors.add(face(ceiling, Direction.DOWN, seed, anchors.size()));
            normals.add(Direction.DOWN.getUnitVec3());
        }
        Direction[] extraDirections = (seed & 32) == 0
                ? new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}
                : new Direction[]{Direction.DOWN, Direction.UP, Direction.SOUTH, Direction.NORTH};
        for (Direction direction : extraDirections) {
            if (anchors.size() == 5) break;
            BlockPos surface = findAnchor(level, mid, direction, 7);
            if (surface == null) continue;
            Vec3 anchor = face(surface, direction.getOpposite(), seed, anchors.size());
            if (anchors.stream().anyMatch(existing -> existing.distanceToSqr(anchor) < 2.25D)) continue;
            anchors.add(anchor);
            normals.add(direction.getOpposite().getUnitVec3());
        }
        // A small connected lattice, including diagonals, reads as a tangled web rather than a rope.
        List<WebPatch.Edge> edges = new ArrayList<>();
        for (int a = 0; a < anchors.size(); a++) for (int b = a + 1; b < anchors.size(); b++)
            edges.add(new WebPatch.Edge(a, b));
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

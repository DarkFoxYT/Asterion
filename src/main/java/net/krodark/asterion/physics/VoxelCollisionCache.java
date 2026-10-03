package net.krodark.asterion.physics;

import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.Function;

/** Reuses overlapping voxel queries within one simulation tick. Never survives a terrain update. */
public final class VoxelCollisionCache {
    private record Cell(int x, int y, int z) { }
    private final Map<Cell, List<AABB>> cells = new HashMap<>();
    private final Function<AABB, Iterable<AABB>> geometry;
    public VoxelCollisionCache(Function<AABB, Iterable<AABB>> geometry) { this.geometry = geometry; }
    public void clear() { cells.clear(); }
    private List<AABB> cell(Cell key) {
        List<AABB> existing = cells.get(key);
        if (existing != null) return existing;
        List<AABB> result = new ArrayList<>();
        for (AABB box : geometry.apply(new AABB(key.x * 4, key.y * 4, key.z * 4,
                key.x * 4 + 4, key.y * 4 + 4, key.z * 4 + 4))) result.add(box);
        if (cells.size() < 512) cells.put(key, result);
        return result;
    }
    public List<AABB> collect(AABB region) {
        List<AABB> result = new ArrayList<>();
        Set<AABB> seen = new HashSet<>();
        for (int x = Math.floorDiv((int)Math.floor(region.minX), 4); x <= Math.floorDiv((int)Math.floor(region.maxX), 4); x++)
            for (int y = Math.floorDiv((int)Math.floor(region.minY), 4); y <= Math.floorDiv((int)Math.floor(region.maxY), 4); y++)
                for (int z = Math.floorDiv((int)Math.floor(region.minZ), 4); z <= Math.floorDiv((int)Math.floor(region.maxZ), 4); z++)
                    for (AABB box : cell(new Cell(x,y,z))) if (box.intersects(region) && seen.add(box)) result.add(box);
        return result;
    }
}

package net.krodark.asterion.test;

import net.krodark.asterion.physics.VoxelCollisionCache;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Storage reuse must never reuse terrain results or mutate previously returned queries. */
final class VoxelCacheLifecycleRegression {
    static void run() {
        var terrain = new ArrayList<AABB>();
        var spanning = new AABB(-5, -2, -5, 5, 2, 5);
        var small = new AABB(-1, -.5, -1, 1, .5, 1);
        terrain.add(spanning); terrain.add(small); terrain.add(spanning);
        var calls = new int[1];
        var cache = new VoxelCollisionCache(area -> { calls[0]++; return terrain; });
        var query = new AABB(-2, -1, -2, 2, 1, 2);
        for (int tick = 0; tick < 1000; tick++) {
            terrain.clear(); terrain.add(spanning); terrain.add(small); terrain.add(spanning);
            cache.clear();
            int before = calls[0];
            var result = cache.collect(query);
            if (!result.equals(List.of(spanning, small))) throw new AssertionError("Collision order/dedup changed");
            int filled = calls[0];
            if (filled <= before || !cache.collect(query).equals(result) || calls[0] != filled)
                throw new AssertionError("Collision cache missed reuse or retained old tick data");
            terrain.clear(); cache.clear();
            if (!cache.collect(query).isEmpty()) throw new AssertionError("Destroyed terrain persisted");
            if (!result.equals(List.of(spanning, small))) throw new AssertionError("Storage reuse mutated an older result");
        }
        cache.clear();
        net.krodark.asterion.Asterion.LOGGER.info("ASTERION_PERF PASS 1000 collision-cache reuse/terrain-change cycles, exact order and independent results");
    }
}

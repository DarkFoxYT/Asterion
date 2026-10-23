import net.krodark.asterion.physics.VoxelCollisionCache;
import net.minecraft.world.phys.AABB;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Same synthetic workload can be run against baseline/fixed classpaths; not an FPS claim. */
public final class VoxelCacheAllocationSmoke {
    public static void main(String[] args) {
        var terrain = new ArrayList<AABB>();
        for (int i = 0; i < 16; i++) terrain.add(new AABB(-8 - i * .01, -8, -8, 8, 8, 8));
        var cache = new VoxelCollisionCache(area -> terrain);
        var region = new AABB(-2, -2, -2, 2, 2, 2);
        var bean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 3000; i++) step(cache, region, terrain);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < 10000; i++) step(cache, region, terrain);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        System.out.println("PASS exact collision results; allocated_bytes_per_tick=" + allocated / 10000.0);
    }
    private static void step(VoxelCollisionCache cache, AABB region, List<AABB> expected) {
        cache.clear();
        if (!cache.collect(region).equals(expected) || !cache.collect(region).equals(expected))
            throw new AssertionError("Collision result order or contents changed");
    }
}

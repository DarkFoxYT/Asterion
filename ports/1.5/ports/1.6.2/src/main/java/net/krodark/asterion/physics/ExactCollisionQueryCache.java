package net.krodark.asterion.physics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.phys.AABB;

/** Exact, ordered broad-phase results for one synchronous simulation tick. */
public final class ExactCollisionQueryCache {
    private final Map<AABB, List<AABB>> queries = new HashMap<>();
    private final Function<AABB, List<AABB>> geometry;
    private final int capacity;

    public ExactCollisionQueryCache(int capacity, Function<AABB, List<AABB>> geometry) {
        if (capacity < 0) throw new IllegalArgumentException("Negative query capacity");
        this.capacity = capacity;
        this.geometry = geometry;
    }

    public List<AABB> collect(AABB bounds) {
        List<AABB> known = queries.get(bounds);
        if (known != null) return known;
        List<AABB> result = geometry.apply(bounds);
        if (queries.size() < capacity) queries.put(bounds, result);
        return result;
    }

    public void clear() { queries.clear(); }
}

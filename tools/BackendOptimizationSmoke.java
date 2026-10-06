import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.AABB;
import net.krodark.asterion.physics.ExactCollisionQueryCache;
import net.krodark.asterion.update.underworld.WebSyncSnapshot;

/** Headless correctness checks and synthetic workload counts, not an FPS benchmark. */
public final class BackendOptimizationSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var calls = new AtomicInteger();
        var first = new AABB(0, 0, 0, 1, 1, 1);
        var second = new AABB(.5, 0, 0, 1.5, 1, 1);
        var ordered = List.of(second, first, second);
        var cache = new ExactCollisionQueryCache(2, bounds -> {
            calls.incrementAndGet();
            return bounds.minX < 2 ? ordered : List.of();
        });
        check(cache.collect(first) == ordered, "Must preserve order and duplicate shapes");
        check(cache.collect(new AABB(0, 0, 0, 1, 1, 1)) == ordered && calls.get() == 1,
                "Equal bounds must reuse results");
        var empty = new AABB(4, 4, 4, 5, 5, 5);
        cache.collect(empty); cache.collect(empty);
        check(calls.get() == 2, "Empty results must be cached");
        var close = new AABB(0, 0, 0, 1 + 1e-10, 1, 1);
        cache.collect(close); cache.collect(close);
        check(calls.get() == 4, "Distinct bounds stay exact; capacity overflow remains correct");
        cache.clear(); cache.collect(first);
        check(calls.get() == 5, "New ticks must refresh terrain");
        var terrain = new ArrayList<AABB>(ordered);
        var changing = new ExactCollisionQueryCache(8, bounds -> List.copyOf(terrain));
        changing.collect(first); terrain.clear(); changing.clear();
        check(changing.collect(first).isEmpty(), "Destroyed terrain cannot survive reset");

        var cuts = new BitSet(); cuts.set(0); cuts.set(4000);
        var viewer = new WebSyncSnapshot();
        check(viewer.needsGeometry(), "New viewers need geometry");
        check(viewer.nextPendingCut(cuts, 0) == 0, "Initial cuts must be sent");
        check(viewer.nextPendingCut(cuts, 0) == 0, "Unsent cuts cannot be acknowledged");
        viewer.geometrySent(); viewer.cutSent(0); viewer.cutSent(4000);
        check(!viewer.needsGeometry() && viewer.nextPendingCut(cuts, 0) == -1,
                "Unchanged state must not be resent");
        cuts.set(12);
        check(viewer.nextPendingCut(cuts, 0) == 12, "New cuts must still arrive");
        check(new WebSyncSnapshot().nextPendingCut(cuts, 0) == 0,
                "Reconnect or re-entry must send the full state");

        var count = new AtomicInteger();
        var repeated = new ExactCollisionQueryCache(512, bounds -> {
            count.incrementAndGet(); return ordered;
        });
        int queries = 0;
        for (int tick = 0; tick < 100; tick++) {
            repeated.clear();
            for (int pass = 0; pass < 20; pass++) for (int body = 0; body < 128; body++) {
                repeated.collect(new AABB(body, 0, 0, body + 1, 1, 1)); queries++;
            }
        }
        check(count.get() == 12800, "Bounded repeated-query workload");
        int packets = 0;
        var sync = new WebSyncSnapshot();
        for (int update = 0; update < 120; update++) {
            if (sync.needsGeometry()) { packets++; sync.geometrySent(); }
            for (int cut = sync.nextPendingCut(cuts, 0); cut >= 0; cut = sync.nextPendingCut(cuts, cut + 1)) {
                packets++; sync.cutSent(cut);
            }
        }
        check(packets == 4, "Stable viewers receive each geometry/cut once");
        System.out.printf("PASS: exact collision ordering, empty results, bounds, capacity, terrain resets, web state and deltas.%nSynthetic repeated-query workload: %,d queries -> %,d terrain collections (95%% fewer).%nStable web workload: 480 packets -> %d packets across 120 updates.%nThese are workload counts, not measured gameplay FPS gains.%n", queries, count.get(), packets);
    }
}

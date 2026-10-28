package net.krodark.asterion.test;

import java.util.Random;
import net.krodark.asterion.client.render.ExactVisibilityCache;

public final class ExactVisibilityCacheRegression {
    private static boolean visibility(double x, double y, double z, double r) {
        return x + r >= -20 && x - r <= 20 && y + r >= 0 && y - r <= 256 && z + r >= -20 && z - r <= 20;
    }
    public static void main(String[] args) {
        var cache = new ExactVisibilityCache();
        var random = new Random(513624);
        long baseline = 0, queries = 0;
        for (int frame = 0; frame < 2000; frame++) {
            cache.clear();
            double[][] lights = new double[96][4];
            for (var light : lights) {
                light[0] = random.nextDouble() * 128 - 64;
                light[1] = random.nextDouble() * 320 - 32;
                light[2] = random.nextDouble() * 128 - 64;
                light[3] = random.nextDouble() * 12;
            }
            for (int pass = 0; pass < 8; pass++) for (var light : lights) {
                double x = light[0], y = light[1], z = light[2], r = light[3];
                boolean expected = visibility(x, y, z, r);
                baseline++;
                int value = cache.lookup(x, y, z, r);
                if (value < 0) { queries++; cache.store(x, y, z, r, expected); value = expected ? 1 : 0; }
                if ((value == 1) != expected) throw new AssertionError("Visibility changed after a collision");
            }
            cache.clear();
            for (var light : lights)
                if (cache.lookup(light[0], light[1], light[2], light[3]) >= 0)
                    throw new AssertionError("Visibility survived a terrain update");
        }
        cache.store(1, 2, 3, 4, true);
        if (cache.lookup(Math.nextUp(1.0), 2, 3, 4) >= 0 || cache.lookup(1, 2, 3, Math.nextUp(4.0)) >= 0)
            throw new AssertionError("Moved illumination volume reused old visibility");
        if (queries >= baseline / 2) throw new AssertionError("Repeated-query fixture did not reduce work");
        System.out.printf(java.util.Locale.ROOT, "PASS %d exact visibility comparisons, 2000 terrain updates; %d/%d renderer queries (%.1f%% avoided), fixed 256-entry cache%n",
                baseline, queries, baseline, 100.0 * (baseline - queries) / baseline);
    }
}

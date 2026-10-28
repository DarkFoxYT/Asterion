package net.krodark.asterion.client.render;

/** Fixed-size, allocation-free memo of exact volumes during one terrain visibility update. */
public final class ExactVisibilityCache {
    private static final int SIZE = 256;
    private final long[] x = new long[SIZE], y = new long[SIZE], z = new long[SIZE], radius = new long[SIZE];
    private final int[] stamps = new int[SIZE];
    private final byte[] values = new byte[SIZE];
    private int generation = 1;

    public void clear() {
        if (++generation == 0) {
            java.util.Arrays.fill(stamps, 0);
            generation = 1;
        }
    }

    /** -1 is a cache miss; collisions simply fall back to the renderer's real query. */
    public int lookup(double px, double py, double pz, double r) {
        long a = Double.doubleToLongBits(px), b = Double.doubleToLongBits(py);
        long c = Double.doubleToLongBits(pz), d = Double.doubleToLongBits(r);
        int slot = slot(a, b, c, d);
        return stamps[slot] == generation && x[slot] == a && y[slot] == b && z[slot] == c && radius[slot] == d
                ? values[slot] : -1;
    }

    public void store(double px, double py, double pz, double r, boolean visible) {
        long a = Double.doubleToLongBits(px), b = Double.doubleToLongBits(py);
        long c = Double.doubleToLongBits(pz), d = Double.doubleToLongBits(r);
        int slot = slot(a, b, c, d);
        x[slot] = a; y[slot] = b; z[slot] = c; radius[slot] = d;
        stamps[slot] = generation;
        values[slot] = (byte)(visible ? 1 : 0);
    }

    private static int slot(long x, long y, long z, long radius) {
        long hash = x * 0x9e3779b97f4a7c15L ^ Long.rotateLeft(y, 21)
                ^ Long.rotateLeft(z * 0xc2b2ae3d27d4eb4fL, 37) ^ radius;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return (int)hash & (SIZE - 1);
    }
}

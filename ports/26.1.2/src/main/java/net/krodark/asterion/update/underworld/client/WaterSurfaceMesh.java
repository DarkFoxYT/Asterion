package net.krodark.asterion.update.underworld.client;

import java.util.Arrays;

/** Two-block open-water quads, with exact one-block coverage along edited water and shores. */
public final class WaterSurfaceMesh {
    private WaterSurfaceMesh() { }
    public static int[] vertices(boolean[] wet, int[] shore) {
        int[] vertices = new int[256 * 4];
        int count = 0;
        for (int z = 0; z < 16; z += 2) for (int x = 0; x < 16; x += 2) {
            boolean merge = true;
            for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++)
                merge &= wet[(z + dz) * 16 + x + dx];
            for (int dz = 0; dz <= 2; dz++) for (int dx = 0; dx <= 2; dx++)
                merge &= shore[(z + dz) * 17 + x + dx] == 255;
            if (merge) count = quad(vertices, count, x, z, 2);
            else for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++)
                if (wet[(z + dz) * 16 + x + dx]) count = quad(vertices, count, x + dx, z + dz, 1);
        }
        return Arrays.copyOf(vertices, count);
    }
    private static int quad(int[] out, int count, int x, int z, int size) {
        out[count++] = z * 17 + x;
        out[count++] = (z + size) * 17 + x;
        out[count++] = (z + size) * 17 + x + size;
        out[count++] = z * 17 + x + size;
        return count;
    }
}

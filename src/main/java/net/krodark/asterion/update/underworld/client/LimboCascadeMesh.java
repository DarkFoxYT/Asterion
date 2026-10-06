package net.krodark.asterion.update.underworld.client;

import java.util.ArrayList;
import java.util.List;

/** Build only exposed drops, once alongside shoreline topology; no world queries per frame. */
public final class LimboCascadeMesh {
    public record Face(int x0, int z0, int x1, int z1, int upper, int lower, int nx, int nz) { }
    private LimboCascadeMesh() { }
    public static List<Face> build(int x, int z, int[] heights, int stride, int margin) {
        var faces = new ArrayList<Face>();
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            int index = (dz + margin) * stride + dx + margin;
            int upper = heights[index];
            if (upper == Integer.MIN_VALUE) continue;
            for (int side = 0; side < 4; side++) {
                int nx = side == 0 ? 1 : side == 1 ? -1 : 0;
                int nz = side == 2 ? 1 : side == 3 ? -1 : 0;
                int lower = heights[index + nx + nz * stride];
                if (lower == Integer.MIN_VALUE || upper - lower < 4) continue;
                int wx = x + dx, wz = z + dz;
                int x0 = wx + (nx > 0 ? 1 : 0), z0 = wz + (nz > 0 ? 1 : 0);
                faces.add(new Face(x0, z0, x0 + (nx == 0 ? 1 : 0),
                        z0 + (nz == 0 ? 1 : 0), upper, lower, nx, nz));
            }
        }
        return List.copyOf(faces);
    }
}

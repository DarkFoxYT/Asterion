package net.krodark.asterion.effect;

import java.util.ArrayList;
import java.util.List;

/** A broken hoplite silhouette, baked once; no named texture or model asset is needed. */
public final class RenderRemnantGeometry {
    public record Vertex(float x, float y, float z, float nx, float ny, float nz, int color) {}
    public static final List<Vertex> VERTICES = bake();
    private RenderRemnantGeometry() {}

    private static List<Vertex> bake() {
        var vertices = new ArrayList<Vertex>();
        int marble = 0xFF000000, dark = 0xFF000000, voidColor = 0xFF000000;
        box(vertices, -.23F, 1.33F, -.17F, .23F, 2.08F, .17F, dark);
        box(vertices, -.21F, 1.40F, -.20F, .21F, 1.96F, -.16F, marble); // fractured cuirass
        box(vertices, -.27F, 1.98F, -.20F, .27F, 2.09F, .20F, marble);
        box(vertices, -.36F, 1.75F, -.14F, -.25F, 2.01F, .12F, dark);
        box(vertices, .25F, 1.75F, -.14F, .37F, 2.01F, .12F, dark);
        box(vertices, -.39F, 1.13F, -.08F, -.28F, 1.76F, .08F, marble);
        box(vertices, .28F, 1.11F, -.08F, .39F, 1.75F, .08F, marble);
        box(vertices, -.19F, .28F, -.10F, -.04F, 1.28F, .11F, dark);
        box(vertices, .04F, .31F, -.10F, .19F, 1.28F, .11F, dark);
        box(vertices, -.20F, .20F, -.19F, -.03F, .41F, .13F, marble);
        box(vertices, .03F, .23F, -.19F, .20F, .44F, .13F, marble);
        box(vertices, -.17F, 2.10F, -.18F, .17F, 2.53F, .17F, voidColor);
        box(vertices, -.21F, 2.47F, -.22F, .21F, 2.61F, .21F, marble);
        box(vertices, -.22F, 2.13F, -.20F, -.14F, 2.48F, .17F, marble);
        box(vertices, .14F, 2.13F, -.20F, .22F, 2.48F, .17F, marble);
        box(vertices, -.035F, 2.25F, -.23F, .035F, 2.49F, -.19F, marble);
        eye(vertices, -.165F, -.045F);
        eye(vertices, .045F, .165F);
        // The bronze helmet's crest is incomplete, as though half the model never loaded.
        box(vertices, -.035F, 2.59F, -.13F, .035F, 2.80F, .04F, 0xFF000000);
        box(vertices, -.035F, 2.64F, .07F, .035F, 2.76F, .16F, 0xFF000000);
        for (int strip = 0; strip < 7; strip++) {
            float x = -.27F + strip * .081F;
            box(vertices, x, .50F + strip % 3 * .13F, .16F, x + .066F, 1.93F, .20F, 0xFF000000);
            box(vertices, x, .10F + strip % 2 * .13F, .15F, x + .035F, .43F + strip % 3 * .05F, .19F, 0xFF000000);
        }
        box(vertices, -.51F, .73F, .08F, -.49F, 1.51F, .10F, 0xFF000000);
        box(vertices, .44F, .49F, -.06F, .47F, .97F, -.04F, 0xFF000000);
        return List.copyOf(vertices);
    }

    private static void eye(List<Vertex> out, float x0, float x1) {
        out.add(new Vertex(x0, 2.33F, -.32F, 0, 0, -1, 0xFFFF0808));
        out.add(new Vertex(x1, 2.33F, -.32F, 0, 0, -1, 0xFFFF0808));
        out.add(new Vertex(x1, 2.43F, -.32F, 0, 0, -1, 0xFFFF0808));
        out.add(new Vertex(x0, 2.43F, -.32F, 0, 0, -1, 0xFFFF0808));
    }

    private static void box(List<Vertex> out, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float[][] p = {{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        int[][] faces = {{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[][] normals = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        for (int face = 0; face < 6; face++) for (int index : faces[face]) {
            float[] v = p[index], n = normals[face];
            out.add(new Vertex(v[0], v[1], v[2], n[0], n[1], n[2], color));
        }
    }
}

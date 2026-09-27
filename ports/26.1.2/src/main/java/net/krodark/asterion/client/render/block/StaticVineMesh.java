package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;

 
final class StaticVineMesh implements VertexConsumer {
    private float[] data = new float[192 * 8];
    private int size, vertex;
    static StaticVineMesh bake(RenderPassInfo<?> pass) {
        var mesh = new StaticVineMesh();
        var stack = pass.poseStack();
        stack.pushPose();
        stack.last().pose().identity();
        stack.last().normal().identity();
        try { pass.renderPosed(() -> pass.model().render(pass, mesh, 0, 0, -1)); }
        finally { stack.popPose(); }
        mesh.removeDegenerateQuads();
        mesh.data = Arrays.copyOf(mesh.data, mesh.size);
        return mesh;
    }
    private void removeDegenerateQuads() {
        int retained = 0;
        for (int offset = 0; offset < size; offset += 32) {
            if (offset + 32 <= size && degenerate(offset, offset + 8, offset + 16)
                    && degenerate(offset, offset + 16, offset + 24)) continue;
            int count = Math.min(32, size - offset);
            System.arraycopy(data, offset, data, retained, count);
            retained += count;
        }
        size = retained;
    }

    private boolean degenerate(int a, int b, int c) {
        double ux = data[b] - data[a], uy = data[b + 1] - data[a + 1], uz = data[b + 2] - data[a + 2];
        double vx = data[c] - data[a], vy = data[c + 1] - data[a + 1], vz = data[c + 2] - data[a + 2];
        return uy * vz == uz * vy && uz * vx == ux * vz && ux * vy == uy * vx;
    }

    void render(PoseStack.Pose pose, VertexConsumer out, int color, int light, int overlay) {
        var position = new org.joml.Vector3f();
        var normal = new org.joml.Vector3f();
        float nx = Float.NaN, ny = Float.NaN, nz = Float.NaN;
        for (int i = 0; i < size; i += 8) {
            pose.pose().transformPosition(data[i], data[i + 1], data[i + 2], position);
            if (nx != data[i + 5] || ny != data[i + 6] || nz != data[i + 7]) {
                nx = data[i + 5]; ny = data[i + 6]; nz = data[i + 7];
                pose.transformNormal(nx, ny, nz, normal);
            }
            out.addVertex(position.x, position.y, position.z, color, data[i + 3], data[i + 4],
                    overlay, light, normal.x, normal.y, normal.z);
        }
    }
    @Override public VertexConsumer addVertex(float x, float y, float z) {
        if (size + 8 > data.length) data = Arrays.copyOf(data, data.length * 2);
        vertex = size; size += 8;
        data[vertex] = x; data[vertex + 1] = y; data[vertex + 2] = z;
        return this;
    }
    @Override public VertexConsumer setUv(float u, float v) { data[vertex + 3] = u; data[vertex + 4] = v; return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) {
        data[vertex + 5] = x; data[vertex + 6] = y; data[vertex + 7] = z; return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setColor(int color) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setLineWidth(float width) { return this; }
}

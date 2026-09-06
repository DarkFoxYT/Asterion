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
        mesh.data = Arrays.copyOf(mesh.data, mesh.size);
        return mesh;
    }
    void verify(RenderPassInfo<?> pass) {
        var expected = new StaticVineMesh();
        pass.renderPosed(() -> pass.model().render(pass, expected, 0, 0, -1));
        var actual = new StaticVineMesh();
        render(pass.poseStack().last(), actual, -1, 0, 0);
        if (expected.size != actual.size) throw new AssertionError("Static vine vertex count changed");
        for (int i = 0; i < expected.size; i++)
            if (Math.abs(expected.data[i] - actual.data[i]) > .0001F)
                throw new AssertionError("Static vine position, UV or normal changed at " + i);
    }
    void render(PoseStack.Pose pose, VertexConsumer out, int color, int light, int overlay) {
        for (int i = 0; i < size; i += 8) {
            out.addVertex(pose, data[i], data[i + 1], data[i + 2]).setColor(color)
                    .setUv(data[i + 3], data[i + 4]).setOverlay(overlay).setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
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

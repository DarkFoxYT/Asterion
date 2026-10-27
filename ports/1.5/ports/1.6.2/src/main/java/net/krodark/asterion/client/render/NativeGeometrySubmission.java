package net.krodark.asterion.client.render;

//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.Arrays;

// Capture vertices during extraction; Minecraft owns the later GPU submission.
public final class NativeGeometrySubmission {
    private final LevelRenderContext context;
    private NativeGeometrySubmission(LevelRenderContext context) { this.context = context; }
    public static NativeGeometrySubmission buffers(LevelRenderContext context) {
        return new NativeGeometrySubmission(context);
    }
    public VertexConsumer getBuffer(RenderType type) {
        var vertices = new CapturedVertices();
        // VertexConsumer's PoseStack overload has already transformed each vertex.
        // Use identity here so the extraction transform is not applied twice.
        context.submitNodeCollector().submitCustomGeometry(new PoseStack(), type, (pose, output) -> vertices.replay(output));
        return vertices;
    }
    public void endBatch(RenderType type) { }

    private static final class CapturedVertices implements VertexConsumer {
        private static final int STRIDE = 14;
        private float[] data = new float[STRIDE * 256];
        private int size;
        private int vertex = -STRIDE;
        public VertexConsumer addVertex(float x, float y, float z) {
            if (size + STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            vertex = size; size += STRIDE;
            data[vertex] = x; data[vertex + 1] = y; data[vertex + 2] = z;
            data[vertex + 3] = Float.intBitsToFloat(0xFFFFFFFF);
            data[vertex + 11] = 1;
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        public VertexConsumer setColor(int color) { data[vertex + 3] = Float.intBitsToFloat(color); return this; }
        public VertexConsumer setUv(float u, float v) { data[vertex + 4] = u; data[vertex + 5] = v; return this; }
        public VertexConsumer setUv1(int u, int v) { data[vertex + 6] = Float.intBitsToFloat((u & 65535) | v << 16); return this; }
        public VertexConsumer setUv2(int u, int v) { data[vertex + 7] = Float.intBitsToFloat((u & 65535) | v << 16); return this; }
        public VertexConsumer setUv3(float u, float v) { data[vertex + 12] = u; data[vertex + 13] = v; return this; }
        public VertexConsumer setNormal(float x, float y, float z) { data[vertex + 8] = x; data[vertex + 9] = y; data[vertex + 10] = z; return this; }
        public VertexConsumer setLineWidth(float width) { data[vertex + 11] = width; return this; }
        private void replay(VertexConsumer output) {
            for (int i = 0; i < size; i += STRIDE) {
                int overlay = Float.floatToRawIntBits(data[i + 6]);
                int light = Float.floatToRawIntBits(data[i + 7]);
                output.addVertex(data[i], data[i + 1], data[i + 2])
                    .setColor(Float.floatToRawIntBits(data[i + 3])).setUv(data[i + 4], data[i + 5])
                    .setUv1(overlay & 65535, overlay >>> 16).setUv2(light & 65535, light >>> 16)
                    .setNormal(data[i + 8], data[i + 9], data[i + 10]).setLineWidth(data[i + 11]);
                VertexExtensions.uv3(output, data[i + 12], data[i + 13]);
            }
        }
    }
}
*///?}

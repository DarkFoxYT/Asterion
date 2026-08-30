package net.krodark.asterion.client.light;

import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import org.joml.Vector3f;

/** Shared immutable vertex buffers; old baked models can be collected after resource reload. */
public final class EmissiveBoneMesh {
    // GeoBone.hashCode traverses parent/child cycles. Weak identity keys avoid that recursion
    // while still allowing obsolete baked models to be collected after a resource reload.
    private static final Map<CuboidGeoBone, EmissiveBoneMesh> CACHE = new MapMaker().weakKeys().makeMap();
    private static final ThreadLocal<Vector3f> POSITION = ThreadLocal.withInitial(Vector3f::new);
    // xyz + uv. Normals/lightmaps/overlays are unused by the full-bright material.
    private final float[] vertices;

    public static EmissiveBoneMesh of(CuboidGeoBone bone) {
        return CACHE.computeIfAbsent(bone, EmissiveBoneMesh::new);
    }

    private EmissiveBoneMesh(CuboidGeoBone bone) {
        int count = 0;
        for (GeoCube cube : bone.cubes) {
            if (cube.quads() == null) continue;
            for (GeoQuad quad : cube.quads()) if (quad != null) count += quad.vertices().length;
        }
        vertices = new float[count * 5];
        PoseStack stack = new PoseStack();
        Vector3f pos = new Vector3f();
        int i = 0;
        for (GeoCube cube : bone.cubes) {
            if (cube.quads() == null) continue;
            stack.pushPose();
            cube.translateToPivotPoint(stack);
            cube.rotate(stack);
            cube.translateAwayFromPivotPoint(stack);
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                for (var vertex : quad.vertices()) {
                    stack.last().pose().transformPosition(vertex.posX(), vertex.posY(), vertex.posZ(), pos);
                    vertices[i++] = pos.x;
                    vertices[i++] = pos.y;
                    vertices[i++] = pos.z;
                    vertices[i++] = vertex.texU();
                    vertices[i++] = vertex.texV();
                }
            }
            stack.popPose();
        }
    }

    /** Only the animated pose and tint change each draw; no cube traversal or per-vertex allocation. */
    public void render(PoseStack.Pose pose, VertexConsumer buffer, int color, float uScale, float vScale) {
        Vector3f position = POSITION.get();
        for (int i = 0; i < vertices.length; i += 5) {
            pose.pose().transformPosition(vertices[i], vertices[i + 1], vertices[i + 2], position);
            buffer.addVertex(position.x, position.y, position.z, color,
                    vertices[i + 3] * uScale, vertices[i + 4] * vScale,
                    0, 0x00F000F0, 0, 1, 0);
        }
    }

    /** Brightness changes RGB, not coverage, so dimming cannot make a bulb see-through. */
    public static int dimColor(int argb, float strength) {
        float brightness = Float.isFinite(strength) ? Math.clamp(strength, 0f, 1f) : 0.8f;
        int red = Math.round(((argb >>> 16) & 255) * brightness);
        int green = Math.round(((argb >>> 8) & 255) * brightness);
        int blue = Math.round((argb & 255) * brightness);
        return (argb & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}

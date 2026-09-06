package net.krodark.asterion.client.light;

import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import org.joml.Vector3f;

 
public final class EmissiveBoneMesh {
     
     
    private static final Map<CuboidGeoBone, EmissiveBoneMesh> CACHE = new MapMaker().weakKeys().makeMap();
    private static final ThreadLocal<Vector3f> POSITION = ThreadLocal.withInitial(Vector3f::new);
     
    private final float[] vertices;

    public static EmissiveBoneMesh horizontalPlane(float radius, float y) {
        return new EmissiveBoneMesh(new float[]{
                .5F-radius,y,.5F-radius,.5F,.5F, .5F-radius,y,.5F+radius,.5F,.5F,
                .5F+radius,y,.5F+radius,.5F,.5F, .5F+radius,y,.5F-radius,.5F,.5F});
    }

    public static EmissiveBoneMesh verticalPlane(float halfWidth, float halfHeight, float z) {
        return new EmissiveBoneMesh(new float[]{
                -halfWidth,-halfHeight,z,.5F,.5F, halfWidth,-halfHeight,z,.5F,.5F,
                halfWidth,halfHeight,z,.5F,.5F, -halfWidth,halfHeight,z,.5F,.5F});
    }

    public static EmissiveBoneMesh texturedRune() {
        return new EmissiveBoneMesh(new float[]{
                -.32F,-.32F,0,0,1, .32F,-.32F,0,1,1,
                .32F,.32F,0,1,0, -.32F,.32F,0,0,0});
    }

    private EmissiveBoneMesh(float[] vertices) { this.vertices = vertices; }

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

     
    public com.meekdev.amnetic.client.instanced.MeshData amneticGeometry() {
        int count = vertices.length / 5;
        int[] indices = new int[count / 4 * 6];
        for (int quad = 0; quad < count / 4; quad++) {
            int v = quad * 4, i = quad * 6;
            indices[i] = v; indices[i + 1] = v + 1; indices[i + 2] = v + 2;
            indices[i + 3] = v; indices[i + 4] = v + 2; indices[i + 5] = v + 3;
        }
        var geometry = com.meekdev.amnetic.client.instanced.MeshData.mutable(5, true, count, indices.length);
        geometry.update(vertices, vertices.length, indices, indices.length);
        return geometry;
    }

     
    public void render(PoseStack.Pose pose, VertexConsumer buffer, int color, float uScale, float vScale) {
        Vector3f position = POSITION.get();
        for (int i = 0; i < vertices.length; i += 5) {
            pose.pose().transformPosition(vertices[i], vertices[i + 1], vertices[i + 2], position);
            buffer.addVertex(position.x, position.y, position.z, color,
                    vertices[i + 3] * uScale, vertices[i + 4] * vScale,
                    0, 0x00F000F0, 0, 1, 0);
        }
    }

     
    public static int dimColor(int argb, float strength) {
        float brightness = Float.isFinite(strength) ? Math.clamp(strength, 0f, 1f) : 0.8f;
        int red = Math.round(((argb >>> 16) & 255) * brightness);
        int green = Math.round(((argb >>> 8) & 255) * brightness);
        int blue = Math.round((argb & 255) * brightness);
        return (argb & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}

package net.krodark.asterion.dev.verification;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.krodark.asterion.client.light.EmissiveBoneMesh;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

final class EmissiveMeshRegression {
    static void run() {
        GeoQuad quad = new GeoQuad(new GeoVertex[] {
                new GeoVertex(-1, 0, 0, 0, 0), new GeoVertex(1, 0, 0, 1, 0),
                new GeoVertex(1, 1, 0, 1, 1), new GeoVertex(-1, 1, 0, 0, 1)}, 0, 0, 1, Direction.SOUTH);
        GeoCube cube = new GeoCube(new GeoQuad[] {null, quad}, new Vec3(3, 7, -2),
                new Vec3(.3, -.7, 1.2), new Vec3(2, 1, 0));
        var bone = new CuboidGeoBone(null, "glow", new GeoBone[0], new GeoCube[] {cube},
                new GeoLocator[0], 0, 0, 0, 0, 0, 0);
        var mesh = EmissiveBoneMesh.of(bone);
        if (mesh != EmissiveBoneMesh.of(bone)) throw new AssertionError("Vertex buffer was not reused");
        verifyLinkedBoneCache(cube);
        for (int i = 0; i < 32; i++) {
            PoseStack pose = new PoseStack();
            pose.translate(i * .1, -2, 4);
            pose.scale(.6f, .8f, 1.3f);
            pose.mulPose(new Quaternionf().rotationXYZ(i * .1f, i * -.2f, .8f));
            Recorder actual = new Recorder(), expected = new Recorder();
            mesh.render(pose.last(), actual, 0x77AABBCC, 1, 1);
            pose.pushPose();
            cube.render(pose, expected, 0x00F000F0, 0, 0x77AABBCC);
            pose.popPose();
            if (actual.values.size() != expected.values.size()) throw new AssertionError("Vertex count mismatch");
            for (int k = 0; k < actual.values.size(); k++) {
                if (Math.abs(actual.values.get(k) - expected.values.get(k)) > .00001f)
                    throw new AssertionError("Cached geometry changed pose/UV at " + k);
            }
        }
        if ((EmissiveBoneMesh.dimColor(0x77FFFFFF, .65f) >>> 24) != 0x77)
            throw new AssertionError("Brightness changed coverage");
        if (EmissiveBoneMesh.dimColor(0xFFFFFFFF, 12f) != 0xFFFFFFFF)
            throw new AssertionError("Legacy HDR strength not clamped");
        System.out.println("PASS: cached bone vertices match GeckoLib through 32 animated poses; linked-bone identity, cache reuse and opacity verified");
    }

    private static void verifyLinkedBoneCache(GeoCube cube) {
        // Baked models link both directions. Structural GeoBone.hashCode recurses forever here.
        GeoBone[] children = new GeoBone[1];
        var parent = new CuboidGeoBone(null, "bulb", children, new GeoCube[0],
                new GeoLocator[0], 0, 0, 0, 0, 0, 0);
        var glow = new CuboidGeoBone(parent, "glow", new GeoBone[0], new GeoCube[] {cube},
                new GeoLocator[0], 0, 0, 0, 0, 0, 0);
        children[0] = glow;
        var mesh = EmissiveBoneMesh.of(glow);
        if (mesh != EmissiveBoneMesh.of(glow)) throw new AssertionError("Linked bone cache miss");
        var parentMesh = EmissiveBoneMesh.of(parent);
        if (parentMesh != EmissiveBoneMesh.of(parent) || parentMesh == mesh)
            throw new AssertionError("Parent and child cache identity mismatch");

        // Resource reloads create new bones, even when their geometry and names are identical.
        var replacement = new CuboidGeoBone(parent, "glow", new GeoBone[0], new GeoCube[] {cube},
                new GeoLocator[0], 0, 0, 0, 0, 0, 0);
        var replacementMesh = EmissiveBoneMesh.of(replacement);
        if (replacementMesh == mesh || replacementMesh != EmissiveBoneMesh.of(replacement))
            throw new AssertionError("Distinct baked bones share a cache entry");
        children[0] = replacement;
        if (mesh != EmissiveBoneMesh.of(glow) || parentMesh != EmissiveBoneMesh.of(parent))
            throw new AssertionError("Hierarchy mutation invalidated identity lookup");
    }

    private static final class Recorder implements VertexConsumer {
        final List<Float> values = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) { values.add(x); values.add(y); values.add(z); return this; }
        public VertexConsumer setUv(float u, float v) { values.add(u); values.add(v); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setColor(int argb) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }
}

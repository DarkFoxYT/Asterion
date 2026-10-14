package net.krodark.asterion.client;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.compat.FlashbackAppearanceCompatibility;
import net.krodark.asterion.effect.RenderRemnantGeometry;
import net.krodark.asterion.effect.WitnessCaptureFence;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** A depth-tested silhouette after atmosphere compositing, with a separate eye bloom batch. */
public final class RenderResiduePass {
    private record Instance(Matrix4fc transform, Vector4fc effect) {}
    private static boolean initialized;
    private static InstanceRenderContext worldFrame;
    private static boolean surfacePending;
    private static boolean surfaceDrawn, worldDepthCleared;
    private RenderResiduePass() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        register(false);
        register(true);
    }

    public static void beginFrame() {
        worldFrame = null; surfacePending = false; surfaceDrawn = false; worldDepthCleared = false;
    }
    public static void beforeHands() { finishWorld(); worldDepthCleared = true; }
    public static void finishWorld() {
        if (worldFrame == null || !surfacePending || surfaceDrawn || worldDepthCleared || WitnessCaptureFence.hidden()) return;
        surfaceDrawn = true;
        int previous = MainTargetFramebuffer.bind();
        try {
            InstanceMeshRegistry.INSTANCE.render(Asterion.id("render/residue_surface"), worldFrame);
            InstanceMeshRegistry.INSTANCE.render(Asterion.id("render/residue"), worldFrame);
        }
        finally { RenderState.DEFAULT.restore(); MainTargetFramebuffer.restore(previous); }
    }

    private static void register(boolean surface) {
        var transform = new Matrix4f();
        var effect = new Vector4f();
        var submission = new Instance(transform, effect);
        var builder = InstancedMesh.<Instance>builder(InstanceLayout.builder().mat4(2).vec4(6).build(),
                (instance, packer) -> packer.putMat4(instance.transform()).putVec4(instance.effect()))
                .geometry(geometry(surface))
                .shaders(Asterion.id("core/render_residue"), Asterion.id("core/render_residue"))
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.builder().depthTest(true).depthWrite(false)
                        .backfaceCulling(false).blend(RenderState.BlendMode.ALPHA).build())
                .onRender((context, batch) -> {
                    worldFrame = context;
                    if (context.world() == null || WitnessCaptureFence.hidden() || PeripheralWitnessController.visibleWitnesses().isEmpty()
                            || FlashbackAppearanceCompatibility.isPlayingReplay()) return;
                    var camera = context.cameraPos();
                    for (var witness : PeripheralWitnessController.visibleWitnesses()) {
                        if (witness.isRemoved() || witness.level() != context.world() || witness.appearance <= .001F
                                || !batch.visible(witness.getX(), witness.getY() + 1.5, witness.getZ(), 2)) continue;
                        transform.translation((float)(witness.getX() - camera.x), (float)(witness.getY() - camera.y),
                                (float)(witness.getZ() - camera.z)).rotateY((float)Math.toRadians(180 - witness.getYRot()));
                        effect.set(witness.appearance, (float)(System.nanoTime() % 120_000_000_000L) * 1e-9F,
                                (float)Math.toRadians(-witness.getXRot()), witness.getId() & 255);
                        batch.add(submission);
                        if (!surface) surfacePending = true;
                        WitnessCaptureFence.witnessDrawn();
                    }
                });
        if (surface) builder.manual(); else builder.emissive(3.2F);
        builder.register(Asterion.id(surface ? "render/residue_surface" : "render/residue"));
    }

    private static MeshData geometry(boolean surface) {
        var baked = RenderRemnantGeometry.VERTICES.stream()
                .filter(v -> ((v.color() & 0xFFFFFF) == 0xFF0808) != surface).toList();
        int count = baked.size();
        float[] vertices = new float[count * 5];
        int[] indices = new int[count / 4 * 6];
        for (int i = 0; i < count; i++) {
            var vertex = baked.get(i);
            vertices[i*5] = vertex.x(); vertices[i*5+1] = vertex.y(); vertices[i*5+2] = vertex.z();
            boolean eye = (vertex.color() & 0xFFFFFF) == 0xFF0808;
            vertices[i*5+3] = eye ? (i % 4 == 0 || i % 4 == 3 ? 0 : 1) : -2;
            vertices[i*5+4] = eye ? (i % 4 < 2 ? 0 : 1) : -2;
        }
        for (int q = 0; q < count/4; q++) {
            int v = q*4, i = q*6;
            indices[i] = v; indices[i+1] = v+1; indices[i+2] = v+2;
            indices[i+3] = v; indices[i+4] = v+2; indices[i+5] = v+3;
        }
        var mesh = MeshData.mutable(5, true, count, indices.length);
        mesh.update(vertices, vertices.length, indices, indices.length);
        return mesh;
    }
}

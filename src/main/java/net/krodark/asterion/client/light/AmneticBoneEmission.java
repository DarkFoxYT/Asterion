package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.emissive.EmissiveSources;
import com.meekdev.amnetic.client.instanced.*;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Reuses the exact rendered bone poses in Amnetic's depth-tested emissive capture. */
public final class AmneticBoneEmission {
    private static final InstanceLayout LAYOUT = InstanceLayout.builder().mat4(2).vec4(6).vec4(7).build();
    private static final Map<MeshKey, Entry> ENTRIES = new HashMap<>();
    private static boolean initialized;
    private static final SourceContext SOURCE_CONTEXT = new SourceContext();
    private static long submissions;
    public static long submissions() { return submissions; }
    private AmneticBoneEmission() { }

    private static void emit(EmissiveContext context) {
        SOURCE_CONTEXT.frame = context;
        try {
            for (Entry entry : ENTRIES.values()) {
                if (entry.count > 0) InstanceMeshRegistry.INSTANCE.render(entry.id, SOURCE_CONTEXT);
            }
        } finally { SOURCE_CONTEXT.frame = null; }
    }

    public static void submit(Identifier model, EmissiveBoneMesh geometry, Identifier texture,
                              Matrix4fc pose, int color, float uScale, float vScale, float strength,
                              boolean backfaceCulling) {
        if (!Bloom.settings().isEnabled()) return;
        if (!initialized) {
            EmissiveSources.register(Asterion.id("vine_glow"), AmneticBoneEmission::emit);
            // Also clear when bloom is disabled or a capture fails; never reuse last frame's poses.
            Pipeline.add(RenderStage.POST, 11, "Clear bone emission submissions", ctx ->
                    ENTRIES.values().forEach(entry -> entry.count = 0));
            initialized = true;
        }
        MeshKey key = new MeshKey(model, texture, backfaceCulling);
        Entry entry = ENTRIES.get(key);
        if (entry == null || entry.geometry != geometry) {
            Identifier id = Asterion.id("bone_emission/" + model.getNamespace() + "/" + model.getPath()
                    + "/" + texture.getNamespace() + "/" + texture.getPath()
                    + (backfaceCulling ? "/culled" : "/two_sided"));
            if (entry != null) InstanceMeshRegistry.INSTANCE.unregister(id);
            entry = new Entry(id, geometry, texture, backfaceCulling);
            ENTRIES.put(key, entry);
        }
        if (entry.count == entry.poses.size()) entry.poses.add(new Instance());
        Instance instance = entry.poses.get(entry.count++);
        instance.pose.set(pose);
        float gain = Float.isFinite(strength) ? Math.clamp(strength, 0.0F, 4.0F) : 1.0F;
        instance.color.set((color >>> 16 & 255) / 255f * gain,
                (color >>> 8 & 255) / 255f * gain,
                (color & 255) / 255f * gain, (color >>> 24) / 255f);
        instance.uv.set(uScale, vScale, 0, 0);
    }

    private static final class Instance {
        final Matrix4f pose = new Matrix4f();
        final Vector4f color = new Vector4f(), uv = new Vector4f();
    }

    private record MeshKey(Identifier model, Identifier texture, boolean backfaceCulling) { }

    private static final class Entry {
        final Identifier id;
        final EmissiveBoneMesh geometry;
        final Identifier texture;
        final ArrayList<Instance> poses = new ArrayList<>();
        int count;
        Entry(Identifier id, EmissiveBoneMesh geometry, Identifier texture, boolean backfaceCulling) {
            this.id = id;
            this.geometry = geometry;
            this.texture = texture;
            Identifier shader = Asterion.id("bone/emission");
            InstancedMesh.<Instance>builder(LAYOUT, (instance, out) -> out.putMat4(instance.pose)
                            .putVec4(instance.color).putVec4(instance.uv))
                    .geometry(geometry.amneticGeometry()).shaders(shader, shader)
                    .extraSampler("TextureSampler", texture, 0, false)
                    .phase(InstancePhase.WORLD_LAST).manual().emissive()
                    .renderState(RenderState.builder().depthTest(true).depthWrite(false)
                            // Only exposed, front-facing pixels may seed bloom. Rendering the
                            // reverse faces let a glow bone illuminate through its enclosing geo.
                            .backfaceCulling(backfaceCulling).blend(RenderState.BlendMode.ALPHA).build())
                    .onRender((ctx, batch) -> {
                        // Manual mesh: only the official emissive-source hook invokes this draw.
                        for (int i = 0; i < count; i++) batch.add(poses.get(i));
                        submissions += count;
                    }).register(id);
        }
    }

    private static final class SourceContext extends InstanceRenderContext {
        EmissiveContext frame;
        @Override public net.minecraft.client.Minecraft client() { return net.minecraft.client.Minecraft.getInstance(); }
        @Override public net.minecraft.client.multiplayer.ClientLevel world() { return frame.level(); }
        @Override public float deltaTick() { return frame.deltaTick(); }
        @Override public net.minecraft.world.phys.Vec3 cameraPos() { return frame.cameraPos(); }
        @Override public Matrix4fc viewMatrix() { return frame.view(); }
        @Override public Matrix4fc projectionMatrix() { return frame.projection(); }
    }
}

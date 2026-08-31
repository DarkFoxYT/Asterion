package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.bloom.Bloom;
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
    private static final Map<Identifier, Entry> ENTRIES = new HashMap<>();
    private static boolean initialized;
    private static boolean capturing;
    private static long submissions;
    public static long submissions() { return submissions; }
    private AmneticBoneEmission() { }

    public static boolean beginCapture() { boolean previous = capturing; capturing = true; return previous; }
    public static void endCapture(boolean previous) { capturing = previous; }

    public static void submit(Identifier model, EmissiveBoneMesh geometry, Identifier texture,
                              Matrix4fc pose, int color, float uScale, float vScale) {
        if (!Bloom.settings().isEnabled()) return;
        if (!initialized) {
            // Also clear when bloom is disabled or a capture fails; never reuse last frame's poses.
            Pipeline.add(RenderStage.POST, 11, "Clear bone emission submissions", ctx ->
                    ENTRIES.values().forEach(entry -> entry.count = 0));
            initialized = true;
        }
        Entry entry = ENTRIES.get(model);
        if (entry == null || entry.geometry != geometry || !entry.texture.equals(texture)) {
            Identifier id = Asterion.id("bone_emission/" + model.getNamespace() + "/" + model.getPath());
            InstanceMeshRegistry.INSTANCE.unregister(id);
            entry = new Entry(id, geometry, texture);
            ENTRIES.put(model, entry);
        }
        if (entry.count == entry.poses.size()) entry.poses.add(new Instance());
        Instance instance = entry.poses.get(entry.count++);
        instance.pose.set(pose);
        instance.color.set((color >>> 16 & 255) / 255f, (color >>> 8 & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
        instance.uv.set(uScale, vScale, 0, 0);
    }

    private static final class Instance {
        final Matrix4f pose = new Matrix4f();
        final Vector4f color = new Vector4f(), uv = new Vector4f();
    }

    private static final class Entry {
        final EmissiveBoneMesh geometry;
        final Identifier texture;
        final ArrayList<Instance> poses = new ArrayList<>();
        int count;
        Entry(Identifier id, EmissiveBoneMesh geometry, Identifier texture) {
            this.geometry = geometry;
            this.texture = texture;
            Identifier shader = Asterion.id("bone/emission");
            InstancedMesh.<Instance>builder(LAYOUT, (instance, out) -> out.putMat4(instance.pose)
                            .putVec4(instance.color).putVec4(instance.uv))
                    .geometry(geometry.amneticGeometry()).shaders(shader, shader)
                    .extraSampler("TextureSampler", texture, 0, false)
                    .phase(InstancePhase.WORLD_LAST).emissive()
                    .renderState(RenderState.builder().depthTest(true).depthWrite(false)
                            .backfaceCulling(false).blend(RenderState.BlendMode.ALPHA).build())
                    .onRender((ctx, batch) -> {
                        // Normal scene rendering stays on the sharp vanilla surface. Only Amnetic's
                        // explicit emission pass receives these instances; no duplicate scene draw.
                        if (!capturing) return;
                        for (int i = 0; i < count; i++) batch.add(poses.get(i));
                        submissions += count;
                    }).register(id);
        }
    }
}

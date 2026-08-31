package net.krodark.asterion.client.render;

import com.meekdev.amnetic.client.compute.ComputeShader;
import com.meekdev.amnetic.client.instanced.internal.CullTargets;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.particle.AnimatedEmissiveParticle;
import org.joml.Matrix4fc;

/** Opt-in bridge for Asterion's alpha-sorted particle mesh; other Amnetic meshes are untouched. */
public final class ParticleCulling {
    private static OrderedParticleCuller culler;
    private static boolean failed;
    private static long dispatches;
    public static long dispatches() { return dispatches; }
    private ParticleCulling() { }

    public static boolean available() {
        if (!net.krodark.asterion.AsterionConfig.INSTANCE.potatoParticleCulling) return false;
        int count = AnimatedEmissiveParticle.trackedCount();
        // Small batches stay cheap; oversized scenes retain the original nearest-visible budget.
        if (Boolean.getBoolean("asterion.disableGpuParticleCulling") || count < 256 || count > 2048 || failed) return false;
        if (culler == null) {
            ComputeShader scan = null, scatter = null;
            try {
                scan = ComputeShader.load(Asterion.id("shaders/particle/cull_scan.comp"));
                scatter = ComputeShader.load(Asterion.id("shaders/particle/cull_scatter.comp"));
                culler = new OrderedParticleCuller(scan.program(), scatter.program());
            } catch (RuntimeException error) {
                if (scan != null) scan.close();
                if (scatter != null) scatter.close();
                failed = true;
                Asterion.LOGGER.warn("Ordered particle culling unavailable; keeping CPU visibility", error);
                return false;
            }
        }
        return true;
    }

    public static void cull(CullTargets targets, int count, int stride, Matrix4fc projectionView) {
        culler.cull(targets, count, stride, projectionView);
        dispatches++;
    }

    public static void reset() {
        if (culler != null) culler.close();
        culler = null;
        failed = false;
    }
}

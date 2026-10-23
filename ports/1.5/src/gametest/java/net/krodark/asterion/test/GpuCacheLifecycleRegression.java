package net.krodark.asterion.test;

import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.scene.internal.CaptureTarget;
import org.lwjgl.opengl.*;

/** Checks actual GL object lifetime, including lazy recreation after disposal. */
final class GpuCacheLifecycleRegression {
    @SuppressWarnings("unchecked")
    static void run() {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            var ctxField = com.meekdev.amnetic.client.particle.ParticleSimulation.class.getDeclaredField("ctx");
            ctxField.setAccessible(true);
            var ctx = (com.meekdev.amnetic.client.particle.ParticleContext)ctxField.get(com.meekdev.amnetic.client.particle.ParticleSimulation.INSTANCE);
            if (ctx.world() != null) throw new AssertionError("Particle context retains its simulation world between frames");
            var field = LayerSystem.class.getDeclaredField("targets"); field.setAccessible(true);
            var targets = (java.util.Map<RenderLayer, CaptureTarget>)field.get(LayerSystem.INSTANCE);
            LayerSystem.INSTANCE.dispose();
            for (int cycle = 0; cycle < 16; cycle++) {
                var capture = new CaptureTarget(); capture.ensure(1F);
                int color = capture.colorTextureGlId(), depth = capture.depthTextureGlId();
                if (color == 0 || depth == 0 || !GL11.glIsTexture(color) || !GL11.glIsTexture(depth))
                    throw new AssertionError("Capture texture was not allocated");
                targets.put(RenderLayer.HAND, capture);
                LayerSystem.INSTANCE.dispose();
                LayerSystem.INSTANCE.dispose();
                if (!targets.isEmpty() || GL11.glIsTexture(color) || GL11.glIsTexture(depth))
                    throw new AssertionError("Disposed layer retains GPU textures");
            }
            net.krodark.asterion.Asterion.LOGGER.info("ASTERION_PERF PASS 16 layer GPU allocate/dispose/recreate cycles; color/depth textures deleted");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture);
            com.mojang.blaze3d.systems.RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new AssertionError("GPU lifecycle GL error: " + error);
    }
}

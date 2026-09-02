package net.krodark.asterion.dev.verification;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.pipeline.FrameContext;
import com.meekdev.amnetic.client.pipeline.PassHandle;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.krodark.asterion.client.render.portal.AsterionPortalRenderer;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.minecraft.core.BlockPos;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;

/** Reads Amnetic's real emission target, without scene extraction masking a broken portal capture. */
public final class PortalEmissionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        Probe[] probe = new Probe[1];
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 0.5 146 0.5 0 90");
            world.getServer().runCommand("time set midnight");
            context.waitTicks(10);
            context.runOnClient(client -> {
                // Zero disables the scene-brightness prefilter, not explicit emissive mesh capture.
                Bloom.settings().enabled(true).all(false).occlude(true).threshold(0);
                probe[0] = new Probe();
                AsterionPortalRenderer.receive(new GatewayPortalPayload(true, new BlockPos(0, 140, 0), 140, 42L));
            });
            context.waitFor(client -> probe[0].visible(), 600);
            context.takeScreenshot("portal-emission-midnight");
            context.runOnClient(client -> {
                Asterion.LOGGER.info("Portal emission: core={}, halo={}", probe[0].core, probe[0].halo);
                InstanceMeshRegistry.INSTANCE.reloadShaders();
                probe[0].reset();
            });
            context.waitFor(client -> probe[0].visible(), 600);

            // A solid ceiling must suppress both layers in the actual depth-tested emission target.
            world.getServer().runCommand("fill -5 142 -5 5 142 5 stone");
            context.runOnClient(client -> probe[0].reset());
            context.waitFor(client -> probe[0].darkFrames >= 5, 600);
            world.getServer().runCommand("fill -5 142 -5 5 142 5 air");
            context.runOnClient(client -> probe[0].reset());
            context.waitFor(client -> probe[0].visible(), 600);
            context.runOnClient(client -> { AsterionPortalRenderer.close(); probe[0].reset(); });
            context.waitFor(client -> probe[0].darkFrames >= 5, 600);
            Asterion.LOGGER.info("PASS: portal core + halo emit at night, survive shader reload, respect occlusion and clear on close");
        } finally {
            context.runOnClient(client -> {
                if (probe[0] != null) probe[0].close();
                AsterionPortalRenderer.close();
                AsterionEmissiveConfig.apply();
            });
        }
    }

    static final class Probe implements AutoCloseable {
        private final Object renderer;
        private final Field target;
        private final PassHandle handle;
        private FloatBuffer pixels;
        float core, halo;
        int darkFrames, visibleFrames;
        private final double[] first, second;

        Probe() {
            this(new double[] {0.9, 140.075, 0.5}, new double[] {2.15, 140.081, 0.5});
        }

        Probe(double[] first, double[] second) {
            this.first = first; this.second = second;
            try {
                Field field = Bloom.class.getDeclaredField("RENDERER");
                field.setAccessible(true);
                renderer = field.get(null);
                target = renderer.getClass().getDeclaredField("emissiveBuf");
                target.setAccessible(true);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            handle = Pipeline.add(RenderStage.POST, 11, "Portal emission verification", this::read);
        }

        void reset() { core = halo = 0; darkFrames = visibleFrames = 0; }
        boolean visible() { return visibleFrames >= 5; }

        private void read(FrameContext frame) {
            try {
                Framebuffer buffer = (Framebuffer) target.get(renderer);
                if (buffer == null || !buffer.isAllocated() || frame.camera() == null) return;
                int size = buffer.width() * buffer.height() * 4;
                if (pixels == null || pixels.capacity() != size) {
                    if (pixels != null) MemoryUtil.memFree(pixels);
                    pixels = MemoryUtil.memAllocFloat(size);
                }
                int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
                int[] packNames = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
                        GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES};
                int[] packValues = new int[packNames.length];
                for (int i = 0; i < packNames.length; i++) packValues[i] = GL11.glGetInteger(packNames[i]);
                try {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                    for (int i = 0; i < packNames.length; i++) GL11.glPixelStorei(packNames[i], i == 0 ? 4 : 0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, buffer.colorTextureGlId(0));
                    GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
                } finally {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
                    for (int i = 0; i < packNames.length; i++) GL11.glPixelStorei(packNames[i], packValues[i]);
                }
                core = sample(frame.camera(), buffer, first[0], first[1], first[2]);
                // Outside the new 3x3 core, inside its restrained square halo.
                halo = sample(frame.camera(), buffer, second[0], second[1], second[2]);
                visibleFrames = core > 0.01F && halo > 0.001F ? visibleFrames + 1 : 0;
                darkFrames = core < 0.00001F && halo < 0.00001F ? darkFrames + 1 : 0;
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }

        private float sample(CameraSnapshot camera, Framebuffer buffer, double x, double y, double z) {
            Vector4f clip = camera.viewProj.transform(new Vector4f((float) (x - camera.eye.x),
                    (float) (y - camera.eye.y), (float) (z - camera.eye.z), 1));
            int px = (int) ((clip.x / clip.w * 0.5F + 0.5F) * buffer.width());
            int py = (int) ((clip.y / clip.w * 0.5F + 0.5F) * buffer.height());
            if (clip.w <= 0 || px < 4 || px >= buffer.width() - 4 || py < 4 || py >= buffer.height() - 4)
                throw new AssertionError("Portal emission probe outside viewport");
            float maximum = 0;
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                int offset = ((py + dy) * buffer.width() + px + dx) * 4;
                for (int channel = 0; channel < 3; channel++) {
                    float value = pixels.get(offset + channel);
                    if (!Float.isFinite(value)) throw new AssertionError("Nonfinite portal emission");
                    maximum = Math.max(maximum, value);
                }
            }
            return maximum;
        }

        @Override public void close() {
            handle.remove();
            if (pixels != null) MemoryUtil.memFree(pixels);
        }
    }
}

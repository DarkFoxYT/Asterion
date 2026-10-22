package net.krodark.asterion.test;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import net.krodark.asterion.Asterion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Real GL regression: shader passes may change FBOs while the main texture stays unchanged. */
public final class ShaderFramebufferRegression {
    public static void run() {
        if (Boolean.getBoolean("asterion.shaderPackSmoke")) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                boolean expected = !net.minecraft.client.Minecraft.getInstance().level.dimension().equals(Asterion.ASTERION_LEVEL);
                if ((Boolean) api.getMethod("isShaderPackInUse").invoke(instance) != expected)
                    throw new AssertionError("External shader pack state does not match the current dimension");
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Iris API unavailable", failure); }
        }
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int first = GL30.glGenFramebuffers(), second = GL30.glGenFramebuffers();
        try {
            for (int pass = 0; pass < 2; pass++) {
                int expectedDraw = pass == 0 ? first : second;
                int expectedRead = pass == 0 ? second : first;
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, expectedDraw);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, expectedRead);
                GL11.glViewport(7, 11, 127 + pass, 93 + pass);
                int previous = MainTargetFramebuffer.bind();
                MainTargetFramebuffer.restore(previous);
                check("forward", expectedDraw, expectedRead, pass);
                GBufferTargets.INSTANCE.captureOuterState();
                previous = GBufferTargets.INSTANCE.bind();
                GBufferTargets.INSTANCE.restore(previous);
                check("G-buffer", expectedDraw, expectedRead, pass);
            }
            Asterion.LOGGER.info("ASTERION_SHADER_FRAMEBUFFER PASS: changing draw/read targets and scaled viewports restored");
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL30.glDeleteFramebuffers(first);
            GL30.glDeleteFramebuffers(second);
        }
    }

    private static void check(String stage, int draw, int read, int pass) {
        int[] actual = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, actual);
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != draw
                || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != read
                || !java.util.Arrays.equals(actual, new int[]{7, 11, 127 + pass, 93 + pass}))
            throw new AssertionError(stage + " leaked framebuffer or viewport state, pass " + pass);
    }
}

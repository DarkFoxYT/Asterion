package net.krodark.asterion.port.client;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** The current shader pass, rather than Minecraft's nominal main render target. */
public record AmneticFramebufferState(int draw, int read, int[] viewport) {
    public static AmneticFramebufferState capture() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return new AmneticFramebufferState(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), viewport);
    }

    public void restore() {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}

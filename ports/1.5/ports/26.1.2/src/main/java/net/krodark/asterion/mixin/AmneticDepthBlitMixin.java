package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.framebuffer.internal.GlFramebuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.krodark.asterion.Asterion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GlFramebuffer.class, remap = false)
public abstract class AmneticDepthBlitMixin {
    @Unique private boolean asterion$reportedDepthFailure;

    @WrapOperation(method = {"blitFromMain", "blitDepthFrom"}, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL30;glBlitFramebuffer(IIIIIIIIII)V"), require = 2, expect = 2)
    private void asterion$copyDepth(int sx0, int sy0, int sx1, int sy1,
                                  int dx0, int dy0, int dx1, int dy1,
                                  int mask, int filter, Operation<Void> original) {
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) == 0) {
            original.call(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
            return;
        }
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // The temporary read FBO has only depth attached. Its default color read/
        // draw selections can make it incomplete on older OpenGL implementations.
        GL11.glReadBuffer(GL11.GL_NONE);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, read);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GlStateManager._disableScissorTest();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                // Fail closed: never leave a cleared-to-far depth buffer showing
                // every hidden emissive source when a driver rejects the target.
                boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                double clear = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
                try {
                    GlStateManager._depthMask(true);
                    GL11.glDepthMask(true);
                    GL11.glClearDepth(0.0);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                } finally {
                    GL11.glClearDepth(clear);
                    GlStateManager._depthMask(write);
                    GL11.glDepthMask(write);
                }
                if (!asterion$reportedDepthFailure) {
                    Asterion.LOGGER.warn("Amnetic depth framebuffer is incomplete; suppressing occluded effects");
                    asterion$reportedDepthFailure = true;
                }
                return;
            }
            original.call(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, GL11.GL_NEAREST);
        } finally {
            if (scissor) {
                GlStateManager._enableScissorTest();
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }
}

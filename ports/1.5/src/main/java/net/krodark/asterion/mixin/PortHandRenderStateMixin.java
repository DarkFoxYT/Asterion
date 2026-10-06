package net.krodark.asterion.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Hands use their own foreground depth, independent of world post effects. */
@Mixin(ItemInHandRenderer.class)
public abstract class PortHandRenderStateMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void asterion$restoreHandState(CallbackInfo callback) {
        // Raw post-processing programs do not update vanilla's ShaderInstance cache.
        // Invalidate it before a hand material reuses the world's shader.
        // Fullscreen passes also bind their own VAO outside BufferUploader.
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        var shader = RenderSystem.getShader();
        if (shader != null) shader.clear();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glColorMask(true, true, true, true);
        var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        target.bindWrite(true);
        GL11.glDrawBuffer(org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
        RenderSystem.viewport(0, 0, target.viewWidth, target.viewHeight);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glFrontFace(GL11.GL_CCW);
        RenderSystem.depthMask(true);
        GL11.glDepthMask(true);
        GL11.glDepthRange(0, 1);
        GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }
}

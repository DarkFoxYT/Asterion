package net.krodark.asterion.mixin;

import net.krodark.asterion.effect.WitnessCaptureFence;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class RenderRemnantCaptureFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void asterion$beginCaptureFrame(CallbackInfo ci) {
        WitnessCaptureFence.beginWorldFrame();
        net.krodark.asterion.client.RenderResiduePass.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void asterion$finishResidueSurface(CallbackInfo ci) { net.krodark.asterion.client.RenderResiduePass.finishWorld(); }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void asterion$preserveWorldOcclusion(CallbackInfo ci) { net.krodark.asterion.client.RenderResiduePass.beforeHands(); }

    @Inject(method = "render", at = @At("RETURN"))
    private void asterion$finishCleanCaptureFrame(CallbackInfo ci) { WitnessCaptureFence.afterWorldFrame(); }
}

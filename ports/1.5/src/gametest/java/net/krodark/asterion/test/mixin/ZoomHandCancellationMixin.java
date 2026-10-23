package net.krodark.asterion.test.mixin;

import net.krodark.asterion.test.ZoomRenderingRegression;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Camera;

/** Reproduce a zoom mod cancelling hands after Amnetic has begun its layer. */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class ZoomHandCancellationMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void asterion$zoomHidesHands(CallbackInfo callback) {
        if (ZoomRenderingRegression.hideHands) {
            if (com.meekdev.amnetic.client.pipeline.internal.LayerRedirect.active() != null)
                ZoomRenderingRegression.interruptedLayers++;
            callback.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void asterion$zoomFov(Camera camera, float partial, boolean world, CallbackInfoReturnable<Double> callback) {
        if (world && Boolean.getBoolean("asterion.zoomSmoke"))
            callback.setReturnValue(callback.getReturnValue() / ZoomRenderingRegression.magnification);
    }
}

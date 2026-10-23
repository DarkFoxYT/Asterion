package net.krodark.asterion.test.mixin;

import net.krodark.asterion.test.ZoomRenderingRegression;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
}

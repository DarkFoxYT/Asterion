package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cancelled/replaced projection must never reuse the previous frame's lens. */
@Mixin(value = FrameView.class, remap = false)
public abstract class AmneticFrameProjectionMixin {
    @Shadow private boolean projectionValid;

    @Inject(method = "invalidate", at = @At("RETURN"))
    private void asterion$invalidateProjection(CallbackInfo callback) {
        projectionValid = false;
    }
}

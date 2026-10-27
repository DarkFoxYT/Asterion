package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom mods may replace projection setup or cancel hand rendering altogether. */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class AmneticZoomFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void asterion$beginWorldFrame(CallbackInfo callback) {
        if (!CaptureManager.INSTANCE.isCapturing()) FrameView.INSTANCE.invalidate();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void asterion$finishCancelledHand(CallbackInfo callback) {
        // A later HEAD cancellation can run Amnetic's begin(HAND) but skip its TAIL.
        // end() is inert when the regular hand tail already closed the layer.
        if (!CaptureManager.INSTANCE.isCapturing()) LayerSystem.INSTANCE.end(RenderLayer.HAND);
    }
}

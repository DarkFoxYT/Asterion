package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.internal.LayerSystem;
import com.meekdev.amnetic.client.scene.internal.CaptureTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/** Layer captures are TextureTargets, outside Amnetic's framebuffer registry. */
@Mixin(value = LayerSystem.class, remap = false)
public abstract class AmneticLayerCleanupMixin {
    @Shadow @Final private Map<RenderLayer, CaptureTarget> targets;

    @Inject(method = "dispose", at = @At("HEAD"))
    private void asterion$releaseLayerTargets(CallbackInfo callback) {
        for (CaptureTarget capture : targets.values()) {
            if (capture.target() != null) capture.target().destroyBuffers();
        }
        targets.clear();
        com.meekdev.amnetic.client.pipeline.internal.LayerRedirect.set(null);
    }
}

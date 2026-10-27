package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import net.krodark.asterion.port.client.ShaderCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PostEffectRegistry.class, remap = false)
public abstract class AmneticShaderPostMixin {
    @Inject(method = "applyAll", at = @At("TAIL"))
    private void asterion$invalidateVanillaState(CallbackInfo callback) {
        net.krodark.asterion.client.render.VanillaRenderState.changed();
    }

    @Inject(method = {"applyAll", "captureWorldDepthSnapshot", "capturePostRenderDepthSnapshot",
            "restorePostRenderDepthSnapshotInto"}, at = @At("HEAD"), cancellable = true)
    private void asterion$externalPostProcessing(CallbackInfo callback) {
        if (ShaderCompatibility.externalShadersActive()) callback.cancel();
    }
}

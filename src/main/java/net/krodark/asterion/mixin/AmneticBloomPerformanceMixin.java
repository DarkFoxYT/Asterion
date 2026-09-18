package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BloomRenderer.class, remap = false)
public abstract class AmneticBloomPerformanceMixin {
    @WrapOperation(method = "renderSources", at = @At(value = "INVOKE",
            target = "Lcom/meekdev/amnetic/client/framebuffer/Framebuffer;blitColorFromMain()V"))
    private void asterion$skipUnusedSceneColor(Framebuffer target, Operation<Void> original) {
        // The bundled prefilter reads EmissiveSampler exclusively when this is populated.
        if (!GBufferTargets.INSTANCE.isPopulated()) original.call(target);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/meekdev/amnetic/client/bloom/BloomSettings;threshold()F"))
    private float asterion$skipUnrequestedSceneCapture(BloomSettings settings, Operation<Float> original) {
        // Avoid a scene color/depth copy and prefilter for ordinary bright terrain.
        // Explicit emitters and a populated emissive G-buffer retain their own paths.
        return !AsterionEmissiveConfig.sceneBloomEnabled()
                || AsterionEmissiveConfig.effectiveBloomQuality() == 1 ? 0.0F : original.call(settings);
    }
}

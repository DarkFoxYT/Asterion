package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BloomRenderer.class, remap = false)
public abstract class AmneticBloomPerformanceMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/meekdev/amnetic/client/bloom/BloomSettings;threshold()F"))
    private float asterion$skipSceneCaptureOnLow(BloomSettings settings, Operation<Float> original) {
        // Low bloom uses explicit glow sources. Avoid a second scene color/depth
        // copy and fullscreen prefilter just to find bright ordinary terrain.
        return AsterionEmissiveConfig.effectiveBloomQuality() == 1 ? 0.0F : original.call(settings);
    }
}

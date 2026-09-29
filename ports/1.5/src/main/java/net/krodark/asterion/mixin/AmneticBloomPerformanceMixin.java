package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import net.krodark.asterion.port.client.PortEmissiveConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BloomRenderer.class, remap = false)
public abstract class AmneticBloomPerformanceMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/meekdev/amnetic/client/bloom/BloomSettings;threshold()F"))
    private float asterion$skipUnrequestedSceneCapture(BloomSettings settings, Operation<Float> original) {
        // A positive threshold enables Amnetic's whole-scene fallback, even with all(false).
        // Keep ordinary terrain out of bloom; explicit emitters retain their separate path.
        return PortEmissiveConfig.sceneBloomEnabled() ? original.call(settings) : 0.0F;
    }
}

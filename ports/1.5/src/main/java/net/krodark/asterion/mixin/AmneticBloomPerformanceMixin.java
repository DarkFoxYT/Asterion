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

    @WrapOperation(method = "ensureChain", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/meekdev/amnetic/client/framebuffer/Framebuffers;screen(Ljava/lang/String;FLcom/meekdev/amnetic/client/framebuffer/FramebufferSpec;)Lcom/meekdev/amnetic/client/framebuffer/Framebuffer;"))
    private com.meekdev.amnetic.client.framebuffer.Framebuffer asterion$worldDepthResolution(
            String name, float scale, com.meekdev.amnetic.client.framebuffer.FramebufferSpec spec,
            Operation<com.meekdev.amnetic.client.framebuffer.Framebuffer> original) {
        // Only geometry/depth is full size. The blur chain keeps its configured scale.
        return original.call(name, 1.0F, spec);
    }
}

package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.pipeline.FrameContext;
import net.krodark.asterion.port.client.ShaderCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Pipeline.class, remap = false)
public abstract class AmneticShaderPipelineMixin {
    @Inject(method = "runStage(Lcom/meekdev/amnetic/client/pipeline/RenderStage;Lcom/meekdev/amnetic/client/pipeline/FrameContext;)V",
            at = @At("HEAD"), cancellable = true)
    private static void asterion$externalLighting(RenderStage stage, FrameContext context, CallbackInfo callback) {
        // Even an empty geometry pass resets GL programs/samplers at its tail.
        // Let the external pack own world rendering; retain GUI/hand layer hooks.
        if (stage != RenderStage.AFTER_HAND && stage != RenderStage.BEFORE_GUI && stage != RenderStage.AFTER_GUI
                && ShaderCompatibility.externalShadersActive()) {
            if (stage == RenderStage.GEOMETRY) {
                ((AmneticModelRegistryAccessor) (Object) com.meekdev.amnetic.client.model.internal.ModelRegistry.INSTANCE).asterion$discardPending();
                com.meekdev.amnetic.client.model.internal.GlReaper.drain();
            }
            callback.cancel();
        }
    }
}

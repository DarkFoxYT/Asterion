package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import net.krodark.asterion.Asterion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Limbo atmosphere runs once before bloom, not again at POST_WORLD. */
@Mixin(value = PostEffectRegistry.class, remap = false)
public abstract class AmneticAtmosphereOrderMixin {
    @WrapOperation(method = "applyAll", at = @At(value = "INVOKE", target =
            "Lcom/meekdev/amnetic/client/post/internal/PostEffectEntry;apply(Lcom/meekdev/amnetic/client/post/RenderPhase;F)V"))
    private void asterion$earlyAtmosphere(PostEffectEntry entry, RenderPhase phase,
                                         float delta, Operation<Void> original) {
        if (!entry.getId().equals(Asterion.id("underworld/river_atmosphere")))
            original.call(entry, phase, delta);
    }
}

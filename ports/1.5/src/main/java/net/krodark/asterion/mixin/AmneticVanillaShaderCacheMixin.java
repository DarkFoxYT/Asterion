package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.render.GlState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlState.class, remap = false)
public abstract class AmneticVanillaShaderCacheMixin {
    @Inject(method = "endFullscreen", at = @At("TAIL"))
    private static void asterion$invalidateVanillaCaches(CallbackInfo callback) {
        // Raw Amnetic programs and VAOs bypass vanilla's cached bindings. Restore
        // coherence even when zoom, a spyglass or third person skips the hands.
        BufferUploader.invalidate();
        var shader = RenderSystem.getShader();
        if (shader != null) shader.clear();
    }
}

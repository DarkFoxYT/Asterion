package net.krodark.asterion.mixin;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.krodark.asterion.client.render.VanillaRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
abstract class GlCommandEncoderStateMixin {
    @Shadow private GlProgram lastProgram;
    @Shadow private RenderPipeline lastPipeline;
    @Unique private long asterion$generation;

    @Inject(method = "trySetup", at = @At("HEAD"))
    private void asterion$restoreAfterRawPass(CallbackInfoReturnable<Boolean> callback) {
        long generation = VanillaRenderState.generation();
        if (asterion$generation == generation) return;
        asterion$generation = generation;
        // Let vanilla bind its own shader and uniforms on the next draw.
        lastProgram = null;
        lastPipeline = null;
    }
}

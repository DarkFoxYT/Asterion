package com.meekdev.amnetic.mixin;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep NeoForge's GPU validation enabled while resolving the underlying GL texture. */
@Mixin(value = VanillaCompat.class, remap = false)
public abstract class NeoForgeTextureMixin {
    @Inject(method = "glId(Lcom/mojang/blaze3d/textures/GpuTexture;)I", at = @At("HEAD"), cancellable = true)
    private static void amnetic$unwrapValidatedTexture(GpuTexture texture, CallbackInfoReturnable<Integer> result) {
        if (!(texture instanceof ValidationGpuTexture)) return;
        while (texture instanceof ValidationGpuTexture validated) texture = validated.getRealTexture();
        result.setReturnValue(texture instanceof GlTexture gl ? gl.glId() : 0);
    }
}

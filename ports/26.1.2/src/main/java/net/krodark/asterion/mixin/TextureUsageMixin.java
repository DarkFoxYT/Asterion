package net.krodark.asterion.mixin;

import net.krodark.asterion.client.render.TextureCacheOwner;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureManager.class)
abstract class TextureUsageMixin {
    @Inject(method = "getTexture", at = @At("RETURN"))
    private void asterion$trackTextureUse(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
        if (cir.getReturnValue() instanceof TextureCacheOwner owner) owner.asterion$markUsed();
    }
}

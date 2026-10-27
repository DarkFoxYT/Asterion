//? if <26.1 {
package net.krodark.asterion.mixin;

import net.krodark.asterion.client.MinotaurBodyPicking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class LegacyBodyPickingMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void asterion$pickAnimatedBody(float partial, CallbackInfo ci) {
        MinotaurBodyPicking.pick(Minecraft.getInstance(), partial);
    }
}
//?}

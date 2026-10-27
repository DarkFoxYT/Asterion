package net.krodark.asterion.mixin;

import net.krodark.asterion.client.MinotaurBodyPicking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinotaurBodyPickingMixin {
    @Shadow private int rightClickDelay;
    @Inject(method = "pick", at = @At("RETURN"))
    private void asterion$pickAnimatedBody(float partial, CallbackInfo ci) {
        MinotaurBodyPicking.pick((Minecraft)(Object)this, partial);
    }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void asterion$attackAnimatedBody(CallbackInfoReturnable<Boolean> cir) {
        if (MinotaurBodyPicking.interact((Minecraft)(Object)this, true)) cir.setReturnValue(true);
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void asterion$harvestAnimatedBody(CallbackInfo ci) {
        if (MinotaurBodyPicking.interact((Minecraft)(Object)this, false)) {
            rightClickDelay = 4;
            ci.cancel();
        }
    }
}

package net.krodark.asterion.mixin;

import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
abstract class ForgeToastMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void asterion$keepForgeClear(CallbackInfo ci) {
        if (net.krodark.asterion.client.CrucibleCamera.active()) ci.cancel();
    }
}

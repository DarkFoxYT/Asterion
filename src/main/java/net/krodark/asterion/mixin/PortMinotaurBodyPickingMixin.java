package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortMinotaurBodyPicking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class PortMinotaurBodyPickingMixin {
    @Shadow private int rightClickDelay;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void asterion$attackMinotaurBody(CallbackInfoReturnable<Boolean> callback) {
        if (PortMinotaurBodyPicking.interact((Minecraft)(Object)this, true)) callback.setReturnValue(true);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void asterion$useMinotaurBody(CallbackInfo callback) {
        if (PortMinotaurBodyPicking.interact((Minecraft)(Object)this, false)) {
            rightClickDelay = 4;
            callback.cancel();
        }
    }
}

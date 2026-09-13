package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortCentipedeInteraction;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class PortCentipedeInteractionMixin {
    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void asterion$mountClickedSegment(CallbackInfo callback) {
        if (PortCentipedeInteraction.tryMount((Minecraft)(Object)this)) {
            rightClickDelay = 4;
            callback.cancel();
        }
    }
}

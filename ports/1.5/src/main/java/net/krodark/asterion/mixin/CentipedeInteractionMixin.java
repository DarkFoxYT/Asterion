package net.krodark.asterion.mixin;

import net.krodark.asterion.client.CentipedeInteractionClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class CentipedeInteractionMixin {
    @Shadow private int rightClickDelay;
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void asterion$mountClickedSegment(CallbackInfo ci) {
        if (CentipedeInteractionClient.tryMount((Minecraft)(Object)this)) {
            rightClickDelay = 4;
            ci.cancel();
        }
    }
}

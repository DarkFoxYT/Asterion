package net.krodark.asterion.mixin;

import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class RagdollPlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void asterion$keepLoadedRagdollSkin(CallbackInfoReturnable<PlayerSkin> ci) {
        ci.setReturnValue(DismembermentEngine.preserveRagdollSkin((AbstractClientPlayer)(Object)this, ci.getReturnValue()));
    }
}

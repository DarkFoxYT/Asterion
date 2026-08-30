package net.krodark.asterion.mixin;

import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class RagdollLocalPlayerMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void asterion$freezeVanillaBody(MoverType type, Vec3 movement, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (DismembermentEngine.INSTANCE.isPlayerTumbling(self.getId())
                || net.krodark.asterion.entity.MinotaurEntity.isHeld(self)
                || net.krodark.asterion.client.CinematicControls.locked()) ci.cancel();
    }
}

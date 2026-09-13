package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortRagdolls;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla input/knockback movement from separating the player from its solved body. */
@Mixin(LocalPlayer.class)
public abstract class PortRagdollLocalPlayerMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void asterion$lockRagdollMovement(MoverType type, Vec3 movement, CallbackInfo callback) {
        if (PortRagdolls.localMovementLocked()) callback.cancel();
    }
}

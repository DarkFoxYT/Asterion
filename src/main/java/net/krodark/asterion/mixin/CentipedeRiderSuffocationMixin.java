package net.krodark.asterion.mixin;

import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wall-climbing mount may visually rotate its rider through an axis-aligned wall check. */
@Mixin(Entity.class)
public abstract class CentipedeRiderSuffocationMixin {
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void asterion$centipedeRiderCanBreathe(CallbackInfoReturnable<Boolean> result) {
        Entity entity = (Entity)(Object)this;
        if (entity.getVehicle() instanceof ScarletCentipedeEntity) result.setReturnValue(false);
    }
}

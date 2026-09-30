package net.krodark.asterion.mixin;

import net.krodark.asterion.update.underworld.world.PhlegethonHazard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PhlegethonContactMixin {
    @Inject(method = "baseTick", at = @At("TAIL"))
    private void asterion$fireSea(CallbackInfo ci) {
        PhlegethonHazard.tick((Entity)(Object)this);
    }
}

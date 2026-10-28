package net.krodark.asterion.mixin;

import net.krodark.asterion.game.EnchantmentGameplay;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class EnchantmentArmorTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void asterion$curseArmor(CallbackInfo callback) { EnchantmentGameplay.tickArmor((LivingEntity)(Object)this); }
}

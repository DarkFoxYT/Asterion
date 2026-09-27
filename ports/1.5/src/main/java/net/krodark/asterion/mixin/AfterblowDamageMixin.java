package net.krodark.asterion.mixin;

import net.krodark.asterion.game.WeaponCombatSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

 

@Mixin(LivingEntity.class)
public abstract class AfterblowDamageMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float asterion$addAfterblowCharge(float damage, DamageSource source) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self.level() instanceof ServerLevel level)) return damage;
        float chargedDamage = WeaponCombatSystem.afterblowDamage(source, damage, level.getGameTime());
        if (chargedDamage > damage) ((LivingEntity)(Object)this).invulnerableTime = 0;
        return chargedDamage;
    }
}

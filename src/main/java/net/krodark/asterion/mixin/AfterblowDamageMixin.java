package net.krodark.asterion.mixin;

import net.krodark.asterion.game.WeaponCombatSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Makes the stored counter part of the original hit so armor, attribution,
 * invulnerability frames and multiplayer all observe one damage transaction. */
@Mixin(LivingEntity.class)
public abstract class AfterblowDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float asterion$addAfterblowCharge(float damage, ServerLevel level, DamageSource source) {
        return WeaponCombatSystem.afterblowDamage(source, damage, level.getGameTime());
    }
}

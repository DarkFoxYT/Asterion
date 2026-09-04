package net.krodark.asterion.mixin;

import net.krodark.asterion.effect.ResolveSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class ResolveDamageMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float asterion$applyResolveDamage(float damage, ServerLevel level,
                                               DamageSource source, float originalDamage) {
        if (source.getEntity() instanceof ServerPlayer player
                && (Object)this instanceof LivingEntity target) {
            damage = ResolveSystem.amplifyDamage(player, target, damage);
            damage = net.krodark.asterion.game.WeaponCombatSystem.amplifyTwinbladeDamage(
                    player, target, source, damage);
            return damage;
        }
        return damage;
    }
}

package net.krodark.asterion.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;

 
public final class GreekFireBurn {
    private GreekFireBurn() { }

    public static void ignite(LivingEntity victim, float seconds) {
        if (!victim.fireImmune() && !victim.hasEffect(MobEffects.FIRE_RESISTANCE))
            victim.igniteForSeconds(seconds);
    }

    public static void clearLegacyScar(LivingEntity entity) {
        var health = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(net.krodark.asterion.Asterion.id("singed_scars"));
    }
}

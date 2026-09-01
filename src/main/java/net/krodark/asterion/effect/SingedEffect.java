package net.krodark.asterion.effect;

import net.krodark.asterion.Asterion;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class SingedEffect extends MobEffect {
    public static final Holder.Reference<MobEffect> TYPE = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Asterion.id("singed"), new SingedEffect());
    private SingedEffect() { super(MobEffectCategory.HARMFUL, 0x70FF45); }
    public static void initialize() { }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 4 == 0; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return true;
        level.sendParticles(Asterion.GREEK_FIRE, entity.getX(), entity.getY() + entity.getBbHeight() * .5,
                entity.getZ(), 2, entity.getBbWidth() * .4, entity.getBbHeight() * .35, entity.getBbWidth() * .4, .015);
        return true;
    }
}

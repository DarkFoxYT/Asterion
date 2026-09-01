package net.krodark.asterion.effect;

import net.krodark.asterion.Asterion;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;

/** Hidden, server-synced source marker; ordinary fire keeps its vanilla overlay. */
public final class GreekFireBurn extends MobEffect {
    public static final Holder.Reference<MobEffect> TYPE = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Asterion.id("greek_fire_burn"), new GreekFireBurn());

    private GreekFireBurn() { super(MobEffectCategory.HARMFUL, 0x58FF38); }
    public static void initialize() { }

    public static void ignite(LivingEntity victim, float seconds) {
        if (victim.fireImmune() || victim.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) return;
        boolean fresh = !victim.hasEffect(SingedEffect.TYPE);
        victim.igniteForSeconds(seconds);
        victim.addEffect(new MobEffectInstance(TYPE, Math.max(1, victim.getRemainingFireTicks()), 0, false, false, false));
        victim.addEffect(new MobEffectInstance(SingedEffect.TYPE, Math.max(200, victim.getRemainingFireTicks()), 0, false, false, true));
        if (fresh && victim instanceof net.minecraft.server.level.ServerPlayer player && victim.getRandom().nextFloat() < .30F)
            SingedScars.get(player.level().getServer()).scar(player);
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
    @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        // Player-sized Greek-fire billboards remain intrusive in first person even when
        // moved behind the torso. The status effect and damage remain fully server-side;
        // only its body-attached cosmetic particles are suppressed for players.
        if (entity instanceof net.minecraft.world.entity.player.Player) return entity.isOnFire();
        if ((entity.tickCount & 3) == 0) {
            // Keep the effect wrapped around the body for observers without allowing its
            // one-block billboards to intersect the burning player's first-person camera.
            var horizontalLook = entity.getLookAngle().multiply(1, 0, 1);
            if (horizontalLook.lengthSqr() < 1.0E-5) horizontalLook = new net.minecraft.world.phys.Vec3(0, 0, 1);
            var behind = entity.position().subtract(horizontalLook.normalize().scale(.78))
                    .add(0, entity.getBbHeight() * .42, 0);
            level.sendParticles(Asterion.GREEK_FIRE, behind.x, behind.y, behind.z,
                    2, .14, .20, .14, .010);
        }
        return entity.isOnFire();
    }
}

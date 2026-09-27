package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

public final class ServerLivingEntityEvents {
    private static final List<AllowDamage> ALLOW_DAMAGE_LISTENERS = new ArrayList<>();
    private static final List<AfterDamage> AFTER_DAMAGE_LISTENERS = new ArrayList<>();
    private static final List<AllowDeath> ALLOW_DEATH_LISTENERS = new ArrayList<>();
    private static final List<AfterDeath> AFTER_DEATH_LISTENERS = new ArrayList<>();

    public static final Event<AllowDamage> ALLOW_DAMAGE = ALLOW_DAMAGE_LISTENERS::add;
    public static final Event<AfterDamage> AFTER_DAMAGE = AFTER_DAMAGE_LISTENERS::add;
    public static final Event<AllowDeath> ALLOW_DEATH = ALLOW_DEATH_LISTENERS::add;
    public static final Event<AfterDeath> AFTER_DEATH = AFTER_DEATH_LISTENERS::add;

    static {
        MinecraftForge.EVENT_BUS.addListener(ServerLivingEntityEvents::onAttack);
        MinecraftForge.EVENT_BUS.addListener(ServerLivingEntityEvents::onDamage);
        MinecraftForge.EVENT_BUS.addListener(ServerLivingEntityEvents::onDeath);
    }

    private ServerLivingEntityEvents() {}

    private static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        for (AllowDamage listener : ALLOW_DAMAGE_LISTENERS) {
            if (!listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        float amount = event.getAmount();
        for (AfterDamage listener : AFTER_DAMAGE_LISTENERS) {
            listener.afterDamage(event.getEntity(), event.getSource(), amount, amount, false);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        for (AllowDeath listener : ALLOW_DEATH_LISTENERS) {
            if (!listener.allowDeath(event.getEntity(), event.getSource(), Float.MAX_VALUE)) {
                event.setCanceled(true);
                return;
            }
        }
        for (AfterDeath listener : AFTER_DEATH_LISTENERS) listener.afterDeath(event.getEntity(), event.getSource());
    }

    @FunctionalInterface public interface AllowDamage { boolean allowDamage(LivingEntity entity, DamageSource source, float amount); }
    @FunctionalInterface public interface AfterDamage { void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked); }
    @FunctionalInterface public interface AllowDeath { boolean allowDeath(LivingEntity entity, DamageSource source, float damageAmount); }
    @FunctionalInterface public interface AfterDeath { void afterDeath(LivingEntity entity, DamageSource source); }
}

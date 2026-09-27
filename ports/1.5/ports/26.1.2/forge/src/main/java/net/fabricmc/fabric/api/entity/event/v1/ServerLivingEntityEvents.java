package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Forge living-entity event adapter for the shared server handlers. */
public final class ServerLivingEntityEvents {
    public static final AllowDamageEvent ALLOW_DAMAGE = new AllowDamageEvent();
    public static final AfterDamageEvent AFTER_DAMAGE = new AfterDamageEvent();
    public static final AllowDeathEvent ALLOW_DEATH = new AllowDeathEvent();
    public static final AfterDeathEvent AFTER_DEATH = new AfterDeathEvent();

    private ServerLivingEntityEvents() {}

    @FunctionalInterface public interface AllowDamage { boolean allow(LivingEntity entity, DamageSource source, float amount); }
    @FunctionalInterface public interface AfterDamage { void after(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked); }
    @FunctionalInterface public interface AllowDeath { boolean allow(LivingEntity entity, DamageSource source, float amount); }
    @FunctionalInterface public interface AfterDeath { void after(LivingEntity entity, DamageSource source); }

    public static final class AllowDamageEvent {
        public void register(AllowDamage callback) {
            LivingHurtEvent.BUS.addListener(event ->
                    !event.getEntity().level().isClientSide()
                            && !callback.allow(event.getEntity(), event.getSource(), event.getAmount()));
        }
    }

    public static final class AfterDamageEvent {
        public void register(AfterDamage callback) {
            LivingDamageEvent.BUS.addListener(event -> {
                if (!event.getEntity().level().isClientSide())
                    callback.after(event.getEntity(), event.getSource(), event.getAmount(), event.getAmount(), false);
            });
        }
    }

    public static final class AllowDeathEvent {
        public void register(AllowDeath callback) {
            LivingDeathEvent.BUS.addListener(event ->
                    !event.getEntity().level().isClientSide()
                            && !callback.allow(event.getEntity(), event.getSource(), 0));
        }
    }

    public static final class AfterDeathEvent {
        public void register(AfterDeath callback) {
            LivingDeathEvent.BUS.addListener((event, cancelled) -> {
                if (!event.getEntity().level().isClientSide() && !cancelled)
                    callback.after(event.getEntity(), event.getSource());
            });
        }
    }
}

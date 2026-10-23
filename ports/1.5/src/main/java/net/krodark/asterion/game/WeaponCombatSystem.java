package net.krodark.asterion.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.item.AfterblowItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Afterblow guarding and stored damage. Retired Twinblades have no combat hooks. */
public final class WeaponCombatSystem {
    private static boolean initialized;

    private WeaponCombatSystem() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || !AfterblowItem.tryBlock(player, source, amount));
        ServerLivingEntityEvents.AFTER_DAMAGE.register(WeaponCombatSystem::afterDamage);
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage,
                                    float damageTaken, boolean blocked) {
        if (damageTaken <= 0 || !(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker) return;
        var weapon = attacker.getMainHandItem();
        if (weapon.is(Asterion.AFTERBLOW))
            AfterblowItem.consumeStored(weapon, attacker.level().getGameTime());
    }

    public static float afterblowDamage(DamageSource source, float damage, long gameTime) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker)
            return damage;
        var weapon = attacker.getMainHandItem();
        if (!weapon.is(Asterion.AFTERBLOW)) return damage;
        float stored = damage > 0 && Float.isFinite(damage) ? AfterblowItem.consumeStored(weapon, gameTime) : 0;
        return stored > 0 && Float.isFinite(damage) ? damage + stored : damage;
    }
}

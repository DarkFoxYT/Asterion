package net.krodark.asterion.game;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.item.AfterblowItem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Multiplayer-safe combat state for Afterblow and the Sickened Twinblades. */
public final class WeaponCombatSystem {
    private static final Identifier TWIN_SPEED = Asterion.id("sickened_twinblades_combo_speed");
    private static final int COMBO_TIMEOUT = 30;
    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static boolean initialized;

    private record Combo(int hits, int lastDamageTick) { }

    private WeaponCombatSystem() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || !AfterblowItem.tryBlock(player, amount));
        ServerLivingEntityEvents.AFTER_DAMAGE.register(WeaponCombatSystem::afterDamage);
        ServerTickEvents.END_SERVER_TICK.register(WeaponCombatSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> COMBOS.clear());
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage,
                                    float damageTaken, boolean blocked) {
        if (damageTaken <= 0) return;

        // Taking a real hit ends an active Twinblades chain. A nullified Afterblow hit never
        // reaches this callback, so a successful guard does not count as being hit.
        if (victim instanceof ServerPlayer wounded) endCombo(wounded, true);

        if (!(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker) return;
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.is(Asterion.SICKENED_TWINBLADES)) recordTwinbladeHit(attacker);

        if (weapon.is(Asterion.AFTERBLOW)) {
            float discharge = AfterblowItem.consumeStored(weapon, attacker.level().getGameTime());
            if (discharge > .001F && victim.isAlive())
                victim.hurtServer((ServerLevel)attacker.level(), source, discharge);
        }
    }

    /** Applied before a landed melee hit; hit three arms the bonus for hit four onward. */
    public static float amplifyTwinbladeDamage(ServerPlayer attacker, LivingEntity target,
                                               DamageSource source, float damage) {
        if (source.getDirectEntity() != attacker || !attacker.getMainHandItem().is(Asterion.SICKENED_TWINBLADES))
            return damage;
        Combo combo = COMBOS.get(attacker.getUUID());
        int now = attacker.level().getServer().getTickCount();
        if (combo == null || combo.hits < 3 || now - combo.lastDamageTick > COMBO_TIMEOUT) return damage;
        return damage * (1F + Math.min(1.5F, (combo.hits - 2) * .16F));
    }

    private static void recordTwinbladeHit(ServerPlayer player) {
        int now = player.level().getServer().getTickCount();
        Combo old = COMBOS.get(player.getUUID());
        int hits = old == null || now - old.lastDamageTick > COMBO_TIMEOUT ? 1 : old.hits + 1;
        COMBOS.put(player.getUUID(), new Combo(hits, now));

        // Force the successful strike's visible arm, replacing the normal main-hand swing
        // on alternating hits. This is broadcast so other players see the same cadence.
        player.swing((hits & 1) == 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        updateSpeed(player, hits);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Combo>> iterator = COMBOS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Combo combo = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (!player.isAlive() || !player.getMainHandItem().is(Asterion.SICKENED_TWINBLADES)
                    || now - combo.lastDamageTick > COMBO_TIMEOUT) {
                finishCombo(player, combo, true);
                iterator.remove();
            } else updateSpeed(player, combo.hits);
        }
    }

    private static void endCombo(ServerPlayer player, boolean hunger) {
        Combo combo = COMBOS.remove(player.getUUID());
        if (combo != null) finishCombo(player, combo, hunger);
    }

    private static void finishCombo(ServerPlayer player, Combo combo, boolean hunger) {
        var speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) speed.removeModifier(TWIN_SPEED);
        if (hunger && combo.hits >= 3) {
            int amplifier = Math.min(2, (combo.hits - 3) / 4);
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, amplifier,
                    false, true, true));
        }
    }

    private static void updateSpeed(ServerPlayer player, int hits) {
        var speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;
        if (hits < 3) {
            speed.removeModifier(TWIN_SPEED);
            return;
        }
        double bonus = Math.min(1.8D, (hits - 2) * .18D);
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                TWIN_SPEED, bonus, AttributeModifier.Operation.ADD_VALUE));
    }
}

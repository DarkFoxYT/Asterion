package net.krodark.asterion.effect;

import net.krodark.asterion.Asterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Detects real outnumbered fights and limits Resolve's bonus to participating attackers. */
public final class ResolveSystem {
    private static final double DETECTION_RADIUS = 16.0D;
    private static final double RETENTION_RADIUS_SQUARED = 24.0D * 24.0D;
    private static final int RECENT_ATTACK_TICKS = 100;
    private static final int TARGETING_MEMORY_TICKS = 30;
    private static final Map<UUID, Map<UUID, Integer>> RECENT_ATTACKERS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> ACTIVE_FIGHTERS = new HashMap<>();

    private ResolveSystem() { }

    public static void recordAttack(LivingEntity victim, DamageSource source, float damageTaken) {
        if (!(victim instanceof ServerPlayer player) || damageTaken <= 0.0F) return;
        Entity sourceEntity = source.getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker) || attacker == player
                || attacker.isAlliedTo(player)) return;
        RECENT_ATTACKERS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(attacker.getUUID(), player.level().getServer().getTickCount() + RECENT_ATTACK_TICKS);
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            updatePlayer(player, now);
        }
        RECENT_ATTACKERS.keySet().retainAll(online);
        ACTIVE_FIGHTERS.keySet().retainAll(online);
    }

    private static void updatePlayer(ServerPlayer player, int now) {
        ServerLevel level = (ServerLevel)player.level();
        Map<UUID, Integer> memory = RECENT_ATTACKERS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(DETECTION_RADIUS),
                mob -> mob.isAlive() && mob.getTarget() == player && !mob.isAlliedTo(player)))
            memory.merge(mob.getUUID(), now + TARGETING_MEMORY_TICKS, Math::max);

        memory.entrySet().removeIf(entry -> {
            Entity attacker = level.getEntity(entry.getKey());
            return entry.getValue() < now || !(attacker instanceof LivingEntity living)
                    || !living.isAlive() || living.isAlliedTo(player)
                    || living.distanceToSqr(player) > RETENTION_RADIUS_SQUARED;
        });

        Set<UUID> fighters = Set.copyOf(memory.keySet());
        ACTIVE_FIGHTERS.put(player.getUUID(), fighters);
        int opponentCount = fighters.size();
        if (opponentCount < 2) return;

        int amplifier = Math.min(2, opponentCount - 2);
        MobEffectInstance current = player.getEffect(Asterion.RESOLVE);
        if (current == null || current.getDuration() < 25 || current.getAmplifier() != amplifier)
            player.addEffect(new MobEffectInstance(Asterion.RESOLVE, 45, amplifier,
                    false, true, true));
    }

    public static float amplifyDamage(ServerPlayer player, LivingEntity target, float damage) {
        MobEffectInstance resolve = player.getEffect(Asterion.RESOLVE);
        Set<UUID> fighters = ACTIVE_FIGHTERS.get(player.getUUID());
        if (resolve == null || fighters == null || fighters.size() < 2
                || !fighters.contains(target.getUUID())) return damage;
        return damage * (1.20F + resolve.getAmplifier() * 0.10F);
    }

    public static void clear() {
        RECENT_ATTACKERS.clear();
        ACTIVE_FIGHTERS.clear();
    }
}

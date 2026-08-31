package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.effect.GreekFireBurn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Shared finite gas hazards for spewers, cursed braziers and player weapons. */
public final class GasClouds {
    private static final Map<ServerLevel, List<Cloud>> CLOUDS = new IdentityHashMap<>();
    private GasClouds() { }
    public static void emit(ServerLevel level, Vec3 origin, Vec3 velocity, UUID owner) {
        var clouds = CLOUDS.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (clouds.size() >= 256 || clouds.stream().filter(c -> Objects.equals(c.owner, owner)).count() >= 64) return;
        clouds.add(new Cloud(origin, velocity, owner));
    }
    public static void ignite(ServerLevel level, Vec3 origin, UUID owner) {
        for (var cloud : CLOUDS.getOrDefault(level, List.of()))
            if (cloud.burn == 0 && Objects.equals(cloud.owner, owner) && cloud.pos.distanceToSqr(origin) < 4 * 4
                    && visible(level, origin, cloud.pos)) cloud.burn = 60;
    }
    private static boolean visible(ServerLevel level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }
    public static void tick(MinecraftServer server) {
        for (var entry : CLOUDS.entrySet()) {
            var level = entry.getKey(); var clouds = entry.getValue();
            Set<UUID> hit = new HashSet<>();
            // Delayed propagation makes ignition visibly travel away from the weapon.
            if (level.getGameTime() % 3 == 0) {
                var burning = clouds.stream().filter(c -> c.burn > 0).map(c -> c.pos).toList();
                for (var cloud : clouds) if (cloud.burn == 0)
                    for (Vec3 fire : burning) if (cloud.pos.distanceToSqr(fire) < 6.25 && visible(level, fire, cloud.pos)) {
                        cloud.burn = 60; break;
                    }
            }
            for (var iterator = clouds.iterator(); iterator.hasNext();) {
                var cloud = iterator.next();
                var block = net.minecraft.core.BlockPos.containing(cloud.pos);
                if (++cloud.age > 200 || cloud.burn == 1 || !level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)
                        || !level.getFluidState(block).isEmpty()) { iterator.remove(); continue; }
                if (cloud.burn > 0) cloud.burn--;
                Vec3 next = cloud.pos.add(cloud.velocity);
                if (visible(level, cloud.pos, next)) cloud.pos = next;
                else cloud.velocity = Vec3.ZERO;
                cloud.velocity = cloud.velocity.multiply(.975, .97, .975).add(0, -.001, 0);
                if (cloud.age % 4 == 0) level.sendParticles(cloud.burn > 0 ? Asterion.BOMBARDIER_GAS_FIRE : Asterion.BOMBARDIER_STENCH,
                        cloud.pos.x, cloud.pos.y, cloud.pos.z, 3, .38, .25, .38, .005);
                if (level.getGameTime() % 10 != 0) continue;
                for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(cloud.pos, cloud.pos).inflate(1.2))) {
                    if (!victim.isAlive() || victim.getUUID().equals(cloud.owner) || hit.contains(victim.getUUID())
                            || victim instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())
                            || !visible(level, cloud.pos, victim.getBoundingBox().getCenter())) continue;
                    if (cloud.burn == 0 && victim.isOnFire()) { cloud.burn = 60; continue; }
                    if (cloud.burn == 0) continue;
                    if (cloud.owner != null && level.getEntity(cloud.owner) instanceof ServerPlayer attacker
                            && victim instanceof ServerPlayer player && !attacker.canHarmPlayer(player)) continue;
                    hit.add(victim.getUUID());
                    victim.hurtServer(level, level.damageSources().inFire(), 5);
                    GreekFireBurn.ignite(victim, 4);
                }
            }
        }
        CLOUDS.values().removeIf(List::isEmpty);
    }
    public static void clear() { CLOUDS.clear(); }
    private static final class Cloud {
        Vec3 pos, velocity; final UUID owner; int age, burn;
        Cloud(Vec3 pos, Vec3 velocity, UUID owner) { this.pos = pos; this.velocity = velocity; this.owner = owner; }
    }
}

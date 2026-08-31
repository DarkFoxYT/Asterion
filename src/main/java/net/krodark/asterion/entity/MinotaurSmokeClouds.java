package net.krodark.asterion.entity;

import net.krodark.asterion.Asterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/** Finite, owner-scoped hazards: smoke drifts against terrain, then burns without changing blocks. */
final class MinotaurSmokeClouds {
    private final ArrayList<Cloud> clouds = new ArrayList<>();

    void clear() { clouds.clear(); }

    void emit(MinotaurEntity owner, Vec3 mouth, Vec3 forward) {
        if (clouds.size() >= 32) return;
        var random = owner.getRandom();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 drift = forward.scale(.24 + random.nextDouble() * .18)
                .add(right.scale((random.nextDouble() - .5) * .3)).add(0, .025, 0);
        clouds.add(new Cloud(mouth, drift, 100 + random.nextInt(61)));
    }

    void tick(ServerLevel level, MinotaurEntity owner) {
        // Multiple overlapping clouds must never multiply a single fire pulse's damage.
        var damaged = new HashSet<UUID>();
        for (var iterator = clouds.iterator(); iterator.hasNext();) {
            Cloud cloud = iterator.next();
            if (++cloud.age >= cloud.igniteAt + 80) { iterator.remove(); continue; }
            if (cloud.age < cloud.igniteAt) {
                Vec3 next = cloud.position.add(cloud.velocity);
                var hit = level.clip(new ClipContext(cloud.position, next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, owner));
                cloud.position = hit.getType() == HitResult.Type.MISS ? next
                        : hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.08));
                cloud.velocity = hit.getType() == HitResult.Type.MISS
                        ? cloud.velocity.multiply(.982, 1, .982).add(0, -.003, 0)
                        : hit.getDirection().getStepY() > 0 ? Vec3.ZERO : new Vec3(0, -.06, 0);
                if ((cloud.age & 3) == 0)
                    level.sendParticles(Asterion.MINOTAUR_BELCH_SMOKE, cloud.position.x, cloud.position.y, cloud.position.z,
                            3, 1.1, .55, 1.1, .008);
                continue;
            }
            if (cloud.age == cloud.igniteAt)
                level.playSound(null, cloud.position.x, cloud.position.y, cloud.position.z,
                        SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, .55F, .7F);
            if ((cloud.age & 3) == 0) {
                level.sendParticles(Asterion.GREEK_FIRE, cloud.position.x, cloud.position.y + .25, cloud.position.z,
                        4, .8, .4, .8, .015);
                level.sendParticles(Asterion.GREEK_FIRE_SOOT, cloud.position.x, cloud.position.y + .4, cloud.position.z,
                        1, .65, .2, .65, .01);
            }
            if (level.getGameTime() % 10 != 0) continue;
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(cloud.position, cloud.position).inflate(1.6, 1.2, 1.6))) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator() || damaged.contains(player.getUUID())) continue;
                var sight = level.clip(new ClipContext(cloud.position, player.getBoundingBox().getCenter(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                if (sight.getType() != HitResult.Type.MISS) continue;
                damaged.add(player.getUUID());
                player.hurtServer(level, level.damageSources().inFire(), 4F);
                player.igniteForSeconds(2);
            }
        }
    }

    private static final class Cloud {
        Vec3 position, velocity;
        final int igniteAt;
        int age;
        Cloud(Vec3 position, Vec3 velocity, int igniteAt) {
            this.position = position; this.velocity = velocity; this.igniteAt = igniteAt;
        }
    }
}

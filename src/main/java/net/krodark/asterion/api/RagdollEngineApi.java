package net.krodark.asterion.api;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.world.phys.Vec3;

public final class RagdollEngineApi {
    private RagdollEngineApi() {
    }

    public static void trigger(LivingEntity entity, Vec3 impulse) {
        if (entity == null || entity instanceof MinotaurEntity
                || !entity.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Vec3 velocity = impulse == null ? entity.getDeltaMovement() : impulse;
            Vec3 direction = velocity.lengthSqr() > 1.0e-8 ? velocity.normalize() : entity.getLookAngle();
            DismembermentEngine.INSTANCE.ragdoll(entity, 1, entity.getBoundingBox().getCenter(),
                    direction, Math.max(.15, velocity.length()), false);
        });
    }

    public static boolean detach(LivingEntity entity, int region, Vec3 point, Vec3 impulse, double force) {
        if (entity == null || entity instanceof MinotaurEntity
                || !entity.level().dimension().equals(Asterion.ASTERION_LEVEL)
                || region < 0 || region > 5) {
            return false;
        }
        Vec3 direction = impulse == null || impulse.lengthSqr() < 1.0e-8 ? entity.getLookAngle() : impulse.normalize();
        return DismembermentEngine.INSTANCE.impact(entity, region,
                point == null ? entity.getBoundingBox().getCenter() : point, direction, force, true);
    }
}

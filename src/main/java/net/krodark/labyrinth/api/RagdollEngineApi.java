package net.krodark.labyrinth.api;

import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Public entry point for clean ragdolls and non-graphic part detachment. */
public final class RagdollEngineApi {
    private RagdollEngineApi() { }

    public static void trigger(LivingEntity entity, Vec3 impulse) {
        if (entity == null) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Vec3 velocity = impulse == null ? entity.getDeltaMovement() : impulse;
            Vec3 direction = velocity.lengthSqr() > 1.0e-8 ? velocity.normalize() : entity.getLookAngle();
            DismembermentEngine.INSTANCE.ragdoll(entity, 1, entity.getBoundingBox().getCenter(),
                    direction, Math.max(.15, velocity.length()), false);
        });
    }

    /** Regions: 0 head, 1 torso, 2 right arm, 3 left arm, 4 right leg, 5 left leg. */
    public static boolean detach(LivingEntity entity, int region, Vec3 point, Vec3 impulse, double force) {
        if (entity == null || region < 0 || region > 5) return false;
        Vec3 direction = impulse == null || impulse.lengthSqr() < 1.0e-8 ? entity.getLookAngle() : impulse.normalize();
        return DismembermentEngine.INSTANCE.impact(entity, region,
                point == null ? entity.getBoundingBox().getCenter() : point, direction, force, true);
    }
}

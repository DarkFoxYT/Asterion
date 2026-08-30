package net.krodark.asterion.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.IntFunction;

/** Oriented model-space hit volumes, shared by client picking and server validation. */
public final class CentipedeInteraction {
    private static final AABB BODY = new AABB(-.68, -.46, -.96, .68, .46, 1.43);
    private static final AABB HEAD = new AABB(-.70, -.45, -2.07, .70, .26, -.94);
    public record Hit(int seat, Vec3 point, double distanceSquared) {}

    private CentipedeInteraction() {}

    public static Hit pick(Vec3 eye, Vec3 end, int count, IntFunction<CentipedeChain.Pose> poses) {
        Hit nearest = null;
        for (int i = 0; i < count; i++) {
            var pose = poses.apply(i);
            Vec3 localEye = toLocal(eye, pose), localEnd = toLocal(end, pose);
            var body = BODY.clip(localEye, localEnd);
            if (BODY.contains(localEye)) body = java.util.Optional.of(localEye);
            if (i == 0) {
                var head = HEAD.clip(localEye, localEnd);
                if (HEAD.contains(localEye)) head = java.util.Optional.of(localEye);
                if (head.isPresent() && (body.isEmpty()
                        || head.get().distanceToSqr(localEye) < body.get().distanceToSqr(localEye))) body = head;
            }
            if (body.isEmpty()) continue;
            Vec3 point = toWorld(body.get(), pose);
            double distance = eye.distanceToSqr(point);
            if (nearest == null || distance < nearest.distanceSquared) nearest = new Hit(i, point, distance);
        }
        return nearest;
    }

    public static boolean contains(Vec3 point, int seat, CentipedeChain.Pose pose, double tolerance) {
        Vec3 local = toLocal(point, pose);
        return BODY.inflate(tolerance).contains(local) || seat == 0 && HEAD.inflate(tolerance).contains(local);
    }

    /** Saddle contact is the top of the actual shell, not the larger collision clearance. */
    public static Vec3 saddle(CentipedeChain.Pose pose, int seat) {
        return pose.position().subtract(pose.normal().scale(seat == 0 ? .23 : .42))
                .add(pose.forward().scale(seat == 0 ? 1.45 : 0));
    }

    public static Vec3 toLocal(Vec3 point, CentipedeChain.Pose pose) {
        Vec3 offset = point.subtract(pose.position());
        Vec3 up = pose.normal().scale(-1), right = pose.forward().cross(up);
        return new Vec3(offset.dot(right), offset.dot(up), -offset.dot(pose.forward()));
    }

    public static Vec3 toWorld(Vec3 local, CentipedeChain.Pose pose) {
        Vec3 up = pose.normal().scale(-1), right = pose.forward().cross(up);
        return pose.position().add(right.scale(local.x)).add(up.scale(local.y)).subtract(pose.forward().scale(local.z));
    }
}

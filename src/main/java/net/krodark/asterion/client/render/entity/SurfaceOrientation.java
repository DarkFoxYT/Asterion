package net.krodark.asterion.client.render.entity;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared wall-local frame math for surface-bound mobs, their riders, and the camera. */
public final class SurfaceOrientation {
    private SurfaceOrientation() {
    }

    public static Quaternionf relativeToRenderYaw(Direction surface, Vec3 forward, float renderYaw) {
        return relativeToRenderYaw(surface.getUnitVec3(), forward, renderYaw);
    }

    public static Quaternionf relativeToRenderYaw(Vec3 normal, Vec3 forward, float renderYaw) {
        if (normal.distanceToSqr(Direction.DOWN.getUnitVec3()) < 1.0E-6D) return new Quaternionf();

        Vec3 up = normal.scale(-1.0D);
        forward = forward.subtract(normal.scale(forward.dot(normal)));
        if (forward.lengthSqr() < 1.0E-5D)
            forward = Math.abs(up.y) < 0.9D
                    ? up.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    : up.cross(new Vec3(1.0D, 0.0D, 0.0D));
        forward = forward.normalize();

        // Build the complete desired world frame directly: local +X is right, +Y is away
        // from the surface, and -Z is the direction of travel. Then remove the vanilla
        // renderer's yaw, leaving only the pose-local correction.
        Vec3 right = forward.cross(up).normalize();
        Matrix3f frame = new Matrix3f()
                .setColumn(0, vec(right))
                .setColumn(1, vec(up))
                .setColumn(2, vec(forward.scale(-1.0D)));
        Quaternionf desiredWorld = new Quaternionf().setFromNormalized(frame).normalize();
        Quaternionf baseYaw = new Quaternionf().rotationY((180.0F - renderYaw) * Mth.DEG_TO_RAD);
        return baseYaw.conjugate().mul(desiredWorld).normalize();
    }

    public static Quaternionf cameraTilt(Direction surface) {
        return cameraTilt(surface.getUnitVec3());
    }

    public static Quaternionf cameraTilt(Vec3 attachmentNormal) {
        if (attachmentNormal.distanceToSqr(Direction.DOWN.getUnitVec3()) < 1.0E-6D)
            return new Quaternionf();
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F),
                vec(attachmentNormal.scale(-1.0D)).normalize());
    }

    private static Vector3f vec(Vec3 vector) {
        return new Vector3f((float)vector.x, (float)vector.y, (float)vector.z);
    }
}

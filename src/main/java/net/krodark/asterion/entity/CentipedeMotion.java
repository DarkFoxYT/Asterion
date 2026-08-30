package net.krodark.asterion.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Tick-rate steering and bounded interpolation; no render-frame physics. */
public final class CentipedeMotion {
    private CentipedeMotion() {}

    public static Vec3 steer(Vec3 from, Vec3 toward, Vec3 normal, double maxRadians) {
        from = CentipedeFrame.tangent(from, normal, toward);
        toward = CentipedeFrame.tangent(toward, normal, from);
        Vec3 right = from.cross(normal.scale(-1));
        double angle = Math.atan2(toward.dot(right), toward.dot(from));
        angle = Mth.clamp(angle, -maxRadians, maxRadians);
        return from.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
    }

    public static Vec3 followHeading(Vec3 from, Vec3 toward, Vec3 normal, double response) {
        from = CentipedeFrame.tangent(from, normal, toward);
        toward = CentipedeFrame.tangent(toward, normal, from);
        double angle = Math.acos(Mth.clamp(from.dot(toward), -1, 1));
        // Normalized vector lerp gets stuck forever when facing exactly backwards.
        // Angular easing takes a real arc while preserving belly-down orientation.
        return steer(from, toward, normal, Math.min(.105, angle * response));
    }

    public static Vec3 interpolate(Vec3 a, Vec3 b, Vec3 startVelocity, Vec3 endVelocity, double t) {
        return new Vec3(cubic(a.x, b.x, startVelocity.x, endVelocity.x, t),
                cubic(a.y, b.y, startVelocity.y, endVelocity.y, t),
                cubic(a.z, b.z, startVelocity.z, endVelocity.z, t));
    }

    private static double cubic(double a, double b, double va, double vb, double t) {
        double distance = b - a;
        if (Math.abs(distance) < 1e-9) return a;
        double alpha = Mth.clamp(va / distance, 0, 3), beta = Mth.clamp(vb / distance, 0, 3);
        double magnitude = Math.hypot(alpha, beta);
        if (magnitude > 3) { alpha *= 3 / magnitude; beta *= 3 / magnitude; }
        double t2 = t * t, t3 = t2 * t;
        return a + distance * ((-2 * t3 + 3 * t2) + (t3 - 2 * t2 + t) * alpha + (t3 - t2) * beta);
    }
}

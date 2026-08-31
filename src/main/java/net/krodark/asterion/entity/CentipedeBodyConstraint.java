package net.krodark.asterion.entity;

import net.minecraft.world.phys.Vec3;

/** Conservative core separation. Neighboring sections are allowed to share their joint. */
public final class CentipedeBodyConstraint {
    public static final double CORE_SPACING = 1.45;
    private CentipedeBodyConstraint() {}

    public static Vec3 separate(Vec3 center, Vec3 other, Vec3 normal, Vec3 forward) {
        Vec3 delta = center.subtract(other);
        double distance = delta.length();
        if (distance >= CORE_SPACING) return center;
        Vec3 tangent = delta.subtract(normal.scale(delta.dot(normal)));
        if (tangent.lengthSqr() < 1e-8) tangent = forward.cross(normal.scale(-1));
        tangent = CentipedeFrame.unit(tangent, new Vec3(1, 0, 0));
        // Preserve this section's own support plane rather than piling links vertically.
        double height = delta.dot(normal);
        double wanted = Math.sqrt(Math.max(0, CORE_SPACING * CORE_SPACING - height * height));
        double existing = Math.sqrt(Math.max(0, delta.lengthSqr() - height * height));
        return center.add(tangent.scale(Math.max(0, wanted - existing)));
    }

    public static double movementFraction(Vec3 from, Vec3 move, Vec3 other) {
        Vec3 offset = from.subtract(other);
        double a = move.lengthSqr();
        if (a < 1e-10) return 1;
        double b = offset.dot(move);
        double c = offset.lengthSqr() - CORE_SPACING * CORE_SPACING;
        if (c <= 0) return b >= 0 ? 1 : 0; // Always permit moving out of an existing overlap.
        if (b >= 0) return 1;
        double discriminant = b * b - a * c;
        if (discriminant <= 0) return 1;
        return Math.clamp((-b - Math.sqrt(discriminant)) / a - .001, 0, 1);
    }
}

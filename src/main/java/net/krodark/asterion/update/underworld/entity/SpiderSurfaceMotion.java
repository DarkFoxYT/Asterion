package net.krodark.asterion.update.underworld.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Pure steering rules shared by the crawler and its movement regression checks. */
public final class SpiderSurfaceMotion {
    private SpiderSurfaceMotion() { }
    public static Vec3 tangent(Direction surface, Vec3 vector) {
        Vec3 normal = surface.getUnitVec3();
        return vector.subtract(normal.scale(vector.dot(normal)));
    }
    public static Vec3 heading(Direction surface, Vec3 desired, Vec3 previous, boolean ascending) {
        Vec3 target = tangent(surface, desired);
        if (ascending && surface.getAxis().isHorizontal())
            target = new Vec3(target.x, 0, target.z).normalize().scale(.35).add(0, 1, 0);
        if (target.lengthSqr() < .04) target = tangent(surface, previous);
        if (target.lengthSqr() < .001)
            target = surface.getAxis().isHorizontal() ? new Vec3(0,1,0) : new Vec3(0,0,1);
        return target.normalize();
    }
    public static Vec3 cornerHeading(Direction previous, Direction next, Vec3 heading) {
        Vec3 projected = tangent(next, heading);
        // At an inside corner the old heading points into the new support.
        // Continue away from the old wall instead of stopping or turning back onto it.
        return projected.lengthSqr() > .04 ? projected.normalize() : previous.getUnitVec3().scale(-1);
    }
}

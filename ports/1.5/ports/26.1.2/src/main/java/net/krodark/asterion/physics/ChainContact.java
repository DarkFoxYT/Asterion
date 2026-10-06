package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;

/** Bounded lateral response for a player capsule against a chain link. */
public final class ChainContact {
    private ChainContact() { }
    public record Response(Vec3 correction, Vec3 velocity) { }
    public static Response resolve(Vec3 body, Vec3 link, Vec3 tangent, Vec3 velocity, double radius) {
        Vec3 delta = body.subtract(link);
        if (delta.lengthSqr() >= radius * radius) return null;
        Vec3 lateral = delta.multiply(1, 0, 1);
        double distance = lateral.length();
        Vec3 normal;
        if (distance > 1e-5) normal = lateral.scale(1 / distance);
        else {
            normal = velocity.multiply(-1, 0, -1);
            if (normal.lengthSqr() < 1e-6) normal = new Vec3(tangent.z, 0, -tangent.x);
            if (normal.lengthSqr() < 1e-6) normal = new Vec3(1, 0, 0);
            normal = normal.normalize();
        }
        double target = Math.sqrt(Math.max(0, radius * radius - delta.y * delta.y));
        double penetration = Math.max(0, target - distance);
        Vec3 correction = normal.scale(Math.min(.3, penetration + .003));
        double into = velocity.dot(normal);
        Vec3 resisted = into < 0 ? velocity.subtract(normal.scale(into)) : velocity;
        return new Response(correction, new Vec3(resisted.x * .65, resisted.y, resisted.z * .65));
    }
}

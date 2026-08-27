package net.krodark.labyrinth.client.ragdoll;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Numerical helpers kept allocation-light because they execute for every fluid node. */
final class RagdollMath {
    private RagdollMath() { }

    static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        double lengthSquared = value.lengthSqr();
        return lengthSquared < 1.0e-10 || !Double.isFinite(lengthSquared)
                ? fallback : value.scale(1.0 / Math.sqrt(lengthSquared));
    }

    static Vec3 reflect(Vec3 velocity, Vec3 normal, double restitution, double friction) {
        double normalSpeed = velocity.dot(normal);
        Vec3 normalPart = normal.scale(normalSpeed);
        Vec3 tangentPart = velocity.subtract(normalPart);
        return tangentPart.scale(Math.max(0.0, 1.0 - friction)).subtract(normalPart.scale(restitution));
    }

    /** Stable tangent basis for every one of Minecraft's six block faces. */
    static Basis faceBasis(Direction face, float rotation) {
        Vec3 normal = face.getUnitVec3();
        Vec3 seed = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 tangent = safeNormalize(normal.cross(seed), new Vec3(1, 0, 0));
        Vec3 bitangent = safeNormalize(normal.cross(tangent), new Vec3(0, 0, 1));
        double cosine = Math.cos(rotation);
        double sine = Math.sin(rotation);
        return new Basis(tangent.scale(cosine).add(bitangent.scale(sine)),
                bitangent.scale(cosine).subtract(tangent.scale(sine)), normal);
    }

    /** Approximates the outward surface normal where a ray entered an entity AABB. */
    static Vec3 nearestSurfaceNormal(AABB box, Vec3 point, Vec3 fallback) {
        double[] distances = {
                Math.abs(point.x - box.minX), Math.abs(point.x - box.maxX),
                Math.abs(point.y - box.minY), Math.abs(point.y - box.maxY),
                Math.abs(point.z - box.minZ), Math.abs(point.z - box.maxZ)
        };
        Vec3[] normals = {
                new Vec3(-1, 0, 0), new Vec3(1, 0, 0), new Vec3(0, -1, 0),
                new Vec3(0, 1, 0), new Vec3(0, 0, -1), new Vec3(0, 0, 1)
        };
        int best = 0;
        for (int i = 1; i < distances.length; i++) if (distances[i] < distances[best]) best = i;
        Vec3 result = normals[best];
        return result.dot(fallback) > 0.95 ? fallback.scale(-1) : result;
    }

    /** SplitMix64: deterministic, fast, and visually free of obvious grid correlation. */
    static long mix(long state) {
        state += 0x9E3779B97F4A7C15L;
        state = (state ^ (state >>> 30)) * 0xBF58476D1CE4E5B9L;
        state = (state ^ (state >>> 27)) * 0x94D049BB133111EBL;
        return state ^ (state >>> 31);
    }

    static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    static Vec3 rayBoxExit(AABB box, Vec3 entry, Vec3 direction) {
        double best = Double.POSITIVE_INFINITY;
        if (direction.x > 1.0e-8) best = positiveMin(best, (box.maxX - entry.x) / direction.x);
        else if (direction.x < -1.0e-8) best = positiveMin(best, (box.minX - entry.x) / direction.x);
        if (direction.y > 1.0e-8) best = positiveMin(best, (box.maxY - entry.y) / direction.y);
        else if (direction.y < -1.0e-8) best = positiveMin(best, (box.minY - entry.y) / direction.y);
        if (direction.z > 1.0e-8) best = positiveMin(best, (box.maxZ - entry.z) / direction.z);
        else if (direction.z < -1.0e-8) best = positiveMin(best, (box.minZ - entry.z) / direction.z);
        return Double.isFinite(best) ? entry.add(direction.scale(best)) : entry;
    }

    private static double positiveMin(double current, double candidate) {
        return candidate > 1.0e-5 && candidate < current ? candidate : current;
    }

    static Basis directionBasis(Vec3 direction) {
        Vec3 forward = safeNormalize(direction, new Vec3(0, 0, 1));
        Vec3 seed = Math.abs(forward.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 tangent = safeNormalize(forward.cross(seed), new Vec3(1, 0, 0));
        return new Basis(tangent, safeNormalize(forward.cross(tangent), new Vec3(0, 1, 0)), forward);
    }

    static Vec3 rotateY(Vec3 value, double angle) {
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        return new Vec3(value.x * cosine + value.z * sine, value.y,
                value.z * cosine - value.x * sine);
    }

    record Basis(Vec3 tangent, Vec3 bitangent, Vec3 normal) { }
}



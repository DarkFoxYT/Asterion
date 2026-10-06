package net.krodark.asterion.update.underworld.world;

import net.minecraft.world.phys.Vec3;

/** Shared terrace/flow layout. Boundaries follow the existing biome contours. */
public final class LimboCascades {
    public static final int DROP = 18;
    public static final int COUNT = 4;
    private LimboCascades() { }

    public static int waterY(double x, double z) {
        return UnderworldTerrain.WATER_Y - DROP * LimboSeaRegions.sea(x, z).ordinal();
    }

    public static double boundaryDistance(double x, double z) {
        double distance = LimboSeaRegions.distance(x, z);
        int boundary = Math.clamp((int)Math.round(distance / LimboSeaRegions.REGION_SIZE), 1, COUNT);
        return distance - boundary * LimboSeaRegions.REGION_SIZE;
    }

    public static Vec3 outward(double x, double z) {
        double dz = z - UnderworldTerrain.FERRY_Z;
        double radius = Math.max(1, Math.hypot(x, dz));
        return new Vec3(x / radius + .056 * Math.cos(x * .0007), 0,
                dz / radius + .072 * Math.cos(z * .0009)).normalize();
    }

    public static Vec3 current(double x, double z) {
        double distance = boundaryDistance(x, z);
        if (z < 18 || distance < -24 || distance > 28) return Vec3.ZERO;
        double envelope = 1 - Math.min(1, Math.abs(distance) / (distance < 0 ? 24 : 28));
        return outward(x, z).scale(.065 * envelope * envelope);
    }

    public static boolean crossesTile(int x, int z) {
        int tier = waterY(x, z);
        return tier != waterY(x + 16, z) || tier != waterY(x, z + 16)
                || tier != waterY(x + 16, z + 16) || Math.abs(boundaryDistance(x + 8, z + 8)) < 28;
    }
}

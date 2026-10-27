package net.krodark.asterion.compat;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Block and box centres retain their original half-block offset after the API change. */
public final class PositionCenters {
    private PositionCenters() { }
    public static Vec3 center(Vec3i position) { return Vec3.atCenterOf(position); }
    public static Vec3 center(AABB bounds) { return bounds.getCenter(); }
}

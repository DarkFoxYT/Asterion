package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;

/** Analytic circle: an entire lap closes at the same point without accumulated steering error. */
public final class ArenaRingPath {
    private ArenaRingPath() { }
    public static Vec3 point(Vec3 center,double radius,double angle) {
        return center.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
    }
    public static double advance(double progress,double speed,double radius) {
        return Math.min(Math.PI*2,progress+speed/radius);
    }
    public static boolean allowed(boolean phaseTwo,int pillars) { return phaseTwo && pillars==0; }
}

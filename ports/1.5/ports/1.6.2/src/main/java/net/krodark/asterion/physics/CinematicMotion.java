package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;
import java.util.function.BiFunction;

/** Analytic spring response and camera clearance, sampled directly from the cinematic clock. */
public final class CinematicMotion {
    private CinematicMotion() { }
    public static double impulse(double age,double kick,double frequency,double damping) {
        if (age<0 || age>120 || !Double.isFinite(age)) return 0;
        return kick*Math.exp(-Math.max(0,damping)*age)*Math.sin(age*frequency);
    }
    public static Vec3 clearCamera(Vec3 focus,Vec3 desired,double radius,BiFunction<Vec3,Vec3,Vec3> raycast) {
        Vec3 span=desired.subtract(focus);
        double distance=span.length();
        if (distance<1.0e-5) return desired;
        Vec3 forward=span.scale(1/distance);
        Vec3 right=forward.cross(Math.abs(forward.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=right.cross(forward).normalize();
        Vec3[] offsets={Vec3.ZERO,right.scale(radius),right.scale(-radius),up.scale(radius),up.scale(-radius),
                right.add(up).scale(radius),right.subtract(up).scale(radius),
                up.subtract(right).scale(radius),right.add(up).scale(-radius)};
        double allowed=distance;
        for (Vec3 offset:offsets) {
            Vec3 hit=raycast.apply(focus.add(offset),desired.add(offset));
            if (hit!=null) allowed=Math.min(allowed,Math.max(0,hit.subtract(focus.add(offset)).dot(forward)-radius));
        }
        return focus.add(forward.scale(allowed));
    }
}

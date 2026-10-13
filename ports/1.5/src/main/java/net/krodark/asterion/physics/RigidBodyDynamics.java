package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Tick-based rigid motion shared by bodies, thrown doors and their fragments. */
public final class RigidBodyDynamics {
    private RigidBodyDynamics() { }

    /** Integrate world angular velocity without Euler-axis ordering or substep-dependent spin. */
    public static void rotate(Quaternionf orientation, double x, double y, double z, double dt) {
        double speed = Math.sqrt(x*x + y*y + z*z);
        if (!Double.isFinite(speed) || !Double.isFinite(dt) || dt <= 0 || speed < 1.0e-10) return;
        double halfAngle = speed*dt*.5, scale = Math.sin(halfAngle)/speed;
        float qx=(float)(x*scale), qy=(float)(y*scale), qz=(float)(z*scale), qw=(float)Math.cos(halfAngle);
        float ox=orientation.x, oy=orientation.y, oz=orientation.z, ow=orientation.w;
        orientation.set(qw*ox+qx*ow+qy*oz-qz*oy, qw*oy-qx*oz+qy*ow+qz*ox,
                qw*oz+qx*oy-qy*ox+qz*ow, qw*ow-qx*ox-qy*oy-qz*oz).normalize();
    }

    /** Keep the largest clear part of a blocked rotation instead of snapping back and reversing spin. */
    public static double clampRotation(Quaternionf orientation, Quaternionf previous,
                                       java.util.function.BooleanSupplier clear) {
        Quaternionf desired=new Quaternionf(orientation);
        orientation.set(previous);
        if (!clear.getAsBoolean()) return 0;
        double low=0,high=1;
        for (int i=0;i<6;i++) {
            double middle=(low+high)*.5;
            orientation.set(previous).slerp(desired,(float)middle).normalize();
            if (clear.getAsBoolean()) low=middle; else high=middle;
        }
        orientation.set(previous).slerp(desired,(float)low).normalize();
        return low;
    }

    /** Separating contacts cannot reflect back into a surface or erase tangential momentum. */
    public static Vec3 reflect(Vec3 velocity, Vec3 normal, double restitution, double friction) {
        double closing = velocity.dot(normal);
        if (closing >= 0) return velocity;
        Vec3 tangent = velocity.subtract(normal.scale(closing));
        double tangentialSpeed = tangent.length();
        double bounce = -closing*net.krodark.asterion.port.compat.MathCompat.clamp(restitution,0,1);
        // Coulomb friction is limited by the normal impulse, rather than a fixed speed multiplier.
        double loss = Math.min(tangentialSpeed, Math.max(0,friction)*(1+net.krodark.asterion.port.compat.MathCompat.clamp(restitution,0,1))*(-closing));
        return (tangentialSpeed > 1.0e-10 ? tangent.scale(1-loss/tangentialSpeed) : Vec3.ZERO)
                .add(normal.scale(bounce));
    }

    /** Remove fracture spray's net momentum while preserving spin-driven fragment motion. */
    public static Vec3 momentumCorrection(Vec3[] velocities, double[] masses, Vec3 parentVelocity) {
        Vec3 momentum=Vec3.ZERO;
        double total=0;
        for (int i=0;i<velocities.length;i++) {
            total+=masses[i];
            momentum=momentum.add(velocities[i].subtract(parentVelocity).scale(masses[i]));
        }
        return total>1.0e-10 ? momentum.scale(1/total) : Vec3.ZERO;
    }

    public static double impactDamage(double normalSpeed, double threshold) {
        double ratio=Math.max(0,normalSpeed)/Math.max(.05,threshold);
        return ratio < .18 ? 0 : Math.min(1.5,ratio*ratio*.35);
    }
}

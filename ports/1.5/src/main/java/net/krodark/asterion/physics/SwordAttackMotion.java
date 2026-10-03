package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Opposite front-quarter cuts, with the blade plane containing the travel direction. */
public final class SwordAttackMotion {
    private SwordAttackMotion() { }
    public static double angle(double aim,int side,double progress) {
        double t=net.krodark.asterion.port.compat.MathCompat.clamp(progress,0,1);t=t*t*(3-2*t);
        return aim+side*(t-.5)*Math.PI/2;
    }
    public static Vec3 point(Vec3 origin,double aim,double radius,int side,double progress) {
        double a=angle(aim,side,progress);
        return origin.add(Math.cos(a)*radius,1.3,Math.sin(a)*radius);
    }
    public static Quaternionf sweepRotation(double angle,int side) {
        Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
        Vec3 travel=new Vec3(-Math.sin(angle)*side,0,Math.cos(angle)*side);
        // The authored sword is thin on X: Y is its length, Z its sharp edge.
        return basis(radial.cross(travel).normalize(),radial);
    }
    public static Quaternionf flightRotation(Vec3 direction) {
        Vec3 blade=direction.normalize();if(blade.lengthSqr()<1e-9)blade=new Vec3(0,0,1);
        Vec3 up=new Vec3(0,1,0);Vec3 edge=up.subtract(blade.scale(up.dot(blade)));
        if(edge.lengthSqr()<1e-6)edge=new Vec3(1,0,0);
        return basis(blade.cross(edge.normalize()).normalize(),blade);
    }
    private static Quaternionf basis(Vec3 x,Vec3 y) {
        Vec3 z=x.cross(y).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x.toVector3f()).setColumn(1,y.toVector3f()).setColumn(2,z.toVector3f())).normalize();
    }
}

package net.krodark.asterion.update.underworld.client;

import net.krodark.asterion.update.underworld.entity.SpiderSurfaceMotion;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Pure world orientation, independent of vanilla body/head yaw and renderer state. */
public final class SpiderRenderFrame {
    private SpiderRenderFrame() { }
    public static Vec3 heading(Vec3 normal,Vec3 previous,Vec3 desired,boolean moving,double elapsed) {
        Vec3 retained=previous.subtract(normal.scale(previous.dot(normal))).normalize();
        Vec3 wanted=desired.subtract(normal.scale(desired.dot(normal))).normalize();
        if(retained.lengthSqr()<.001)return wanted;
        if(!moving || wanted.lengthSqr()<.001)return retained;
        return SpiderSurfaceMotion.turn(normal,retained,wanted,.20*Math.clamp(elapsed,0,2));
    }
    public static Quaternionf orientation(Vec3 normal,Vec3 forward) {
        Vec3 up=normal.scale(-1).normalize();
        Vec3 heading=forward.subtract(up.scale(forward.dot(up))).normalize();
        if(heading.lengthSqr()<.001) {
            Vec3 fallback=Math.abs(up.y)<.9?new Vec3(0,1,0):new Vec3(0,0,-1);
            heading=fallback.subtract(up.scale(fallback.dot(up))).normalize();
        }
        Vec3 right=heading.cross(up).normalize();
        return new Quaternionf().setFromNormalized(new org.joml.Matrix3f()
                .setColumn(0,new org.joml.Vector3f((float)right.x,(float)right.y,(float)right.z))
                .setColumn(1,new org.joml.Vector3f((float)up.x,(float)up.y,(float)up.z))
                .setColumn(2,new org.joml.Vector3f((float)-heading.x,(float)-heading.y,(float)-heading.z))).normalize();
    }

}

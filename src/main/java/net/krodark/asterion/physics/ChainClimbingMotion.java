package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;

/** Collision-respecting velocity toward a smooth orbit; never teleports the player. */
public final class ChainClimbingMotion {
    private ChainClimbingMotion() { }
    public static Vec3 velocity(Vec3 feet,Vec3 center,Vec3 tangent,float yaw,float forward,boolean crouching) {
        Vec3 axis=tangent.normalize();double angle=Math.toRadians(yaw);
        Vec3 aim=new Vec3(Math.sin(angle),0,-Math.cos(angle));
        Vec3 desired=aim.subtract(axis.scale(aim.dot(axis)));
        if(desired.lengthSqr()<1e-6){aim=new Vec3(0,1,0);desired=aim.subtract(axis.scale(aim.dot(axis)));}
        if(desired.lengthSqr()<1e-6)desired=new Vec3(1,0,0);
        desired=desired.normalize();
        Vec3 offset=feet.add(0,.9,0).subtract(center);
        Vec3 current=offset.subtract(axis.scale(offset.dot(axis)));
        if(current.lengthSqr()<1e-6)current=desired;else current=current.normalize();
        double turn=Math.atan2(axis.dot(current.cross(desired)),current.dot(desired));
        turn=Math.clamp(turn,-.48,.48);
        Vec3 orbit=current.scale(Math.cos(turn)).add(axis.cross(current).scale(Math.sin(turn)));
        Vec3 pull=center.add(orbit.scale(.48)).subtract(feet.add(0,.9,0)).scale(.65);
        if(pull.lengthSqr()>.22*.22)pull=pull.normalize().scale(.22);
        return pull.add(axis.scale(crouching?0:Math.clamp(forward,-1,1)*.16));
    }
    public static Vec3 velocity(Vec3 feet,Vec3 center,float yaw,float forward,boolean crouching) {
        double angle=Math.toRadians(yaw),radius=.48;
        double current=Math.atan2(feet.x-center.x,-(feet.z-center.z));
        double turn=Math.atan2(Math.sin(angle-current),Math.cos(angle-current));
        angle=current+Math.clamp(turn,-.48,.48);
        Vec3 desired=new Vec3(center.x+Math.sin(angle)*radius,feet.y,center.z-Math.cos(angle)*radius);
        Vec3 pull=desired.subtract(feet).scale(.65);
        if(pull.lengthSqr()>.22*.22)pull=pull.normalize().scale(.22);
        double climb=crouching?0:Math.clamp(forward,-1,1)*.16;
        return new Vec3(pull.x,climb,pull.z);
    }
}

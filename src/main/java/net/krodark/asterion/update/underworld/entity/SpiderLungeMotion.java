package net.krodark.asterion.update.underworld.entity;

import net.minecraft.world.phys.Vec3;

/** A short, collision-resolved leap, with an impulse chosen for its flight time. */
public final class SpiderLungeMotion {
    private SpiderLungeMotion() { }

    public static Vec3 launch(Vec3 from,Vec3 target,Vec3 targetVelocity) {
        Vec3 delta=target.subtract(from);
        int ticks=(int)Math.clamp(Math.ceil(delta.horizontalDistance()/.85),8,16);
        Vec3 prediction=targetVelocity.multiply(1,0,1).scale(Math.min(ticks,4));
        if(prediction.length()>1.2)prediction=prediction.normalize().scale(1.2);
        delta=delta.add(prediction);
        double horizontalSum=(1-Math.pow(.91,ticks))/.09;
        double verticalSum=(1-Math.pow(.98,ticks))/.02;
        Vec3 horizontal=delta.multiply(1,0,1).scale(1/horizontalSum);
        if(horizontal.length()>1.65)horizontal=horizontal.normalize().scale(1.65);
        double vertical=(delta.y+(.08*.98/.02)*(ticks-verticalSum))/verticalSum;
        // Level lunges must visibly leave the ground; ceiling ambushes dive.
        if(delta.y>-.8)vertical=Math.max(.48,vertical);
        return horizontal.add(0,Math.clamp(vertical,-.85,.95),0);
    }

    public static Vec3 afterTick(Vec3 velocity) {
        return new Vec3(velocity.x*.91,(velocity.y-.08)*.98,velocity.z*.91);
    }
}

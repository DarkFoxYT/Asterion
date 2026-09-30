package net.krodark.asterion.update.underworld.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/** Server-side support probes matching the authored eight-foot fan. */
public final class SpiderStanceSupport {
    private SpiderStanceSupport() { }
    public static int contacts(AABB body,Vec3 up,Vec3 forward,double scale,Predicate<Vec3> supported) {
        double clearance=(Math.abs(up.x)*body.getXsize()+Math.abs(up.y)*body.getYsize()+Math.abs(up.z)*body.getZsize())*.5;
        Vec3 base=body.getCenter().subtract(up.scale(clearance));
        Vec3 side=forward.cross(up).normalize();int count=0;
        for(int leg=0;leg<8;leg++) {
            int ordinal=leg%4;
            double x=(ordinal==0||ordinal==3?.70:1.10)*scale*(leg<4?-1:1);
            double z=(ordinal==0?-.563:ordinal==1?-.125:ordinal==2?.125:.563)*scale;
            Vec3 foot=base.add(side.scale(x)).add(forward.scale(z));
            // Mirror the IK's inward searches before moving the whole body.
            if(supported.test(foot) || supported.test(foot.lerp(base,.22)) || supported.test(foot.lerp(base,.45)))count++;
        }
        return count;
    }
    public static Vec3 reposition(AABB body,Vec3 up,Vec3 forward,double scale,Predicate<Vec3> supported,Predicate<Vec3> clear) {
        int current=contacts(body,up,forward,scale,supported);
        if(current>=4)return Vec3.ZERO;
        Vec3 side=forward.cross(up).normalize(),best=Vec3.ZERO;int most=current;
        for(double distance:new double[]{.16,.32,.48,.64,.96}) {
            for(int direction=0;direction<8;direction++) {
                double angle=direction*Math.PI/4;
                Vec3 shift=forward.scale(Math.cos(angle)*distance).add(side.scale(Math.sin(angle)*distance));
                if(!clear.test(shift))continue;
                int contacts=contacts(body.move(shift),up,forward,scale,supported);
                if(contacts>=4)return shift;
                if(contacts>most) { most=contacts;best=shift; }
            }
        }
        return best;
    }
}

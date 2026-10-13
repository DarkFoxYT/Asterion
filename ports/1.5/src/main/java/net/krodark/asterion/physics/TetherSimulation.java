package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.BiFunction;

/** Persistent, world-space grapple rope, advanced on game ticks and interpolated for rendering. */
public final class TetherSimulation {
    private SegmentedChain chain;
    private long tick=Long.MIN_VALUE;
    private Vec3 start,end;

    public Vec3[] sample(Vec3 from,Vec3 to,double slack,long time,float partial,
                         BiFunction<Vec3,Vec3,Vec3> collision) {
        double length=from.distanceTo(to)+Math.max(0,slack)*.45;
        if (chain==null || time<tick || time-tick>4 || from.distanceToSqr(start)>64 || to.distanceToSqr(end)>64) {
            chain=new SegmentedChain(from,to,net.krodark.asterion.port.compat.MathCompat.clamp((int)Math.ceil(length/.4),8,64),Math.max(.08,length));
            chain.sag(from,to);
            start=from; end=to; tick=time;
        }
        if (time>tick) {
            int steps=(int)(time-tick);
            chain.payout(Math.max(.08,length));
            for (int i=1;i<=steps;i++)
                chain.step(start.lerp(from,i/(double)steps),end.lerp(to,i/(double)steps),true,collision,List.of());
            start=from; end=to; tick=time;
        }
        Vec3[] points=chain.rendered(partial);
        Vec3 startShift=from.subtract(points[0]),endShift=to.subtract(points[points.length-1]);
        for (int i=0;i<points.length;i++) {
            double t=i/(double)(points.length-1);
            points[i]=points[i].add(startShift.scale(1-t)).add(endShift.scale(t));
        }
        points[0]=from; points[points.length-1]=to;
        return points;
    }
}

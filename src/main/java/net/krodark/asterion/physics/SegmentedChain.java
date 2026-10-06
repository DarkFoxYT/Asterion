package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;
import java.util.function.BiFunction;

/** Fixed-timestep Verlet chain with alternating constraint sweeps and collision-aware nodes. */
public final class SegmentedChain {
    private final Vec3[] points, old, previous, smooth;
    private double spacing;
    public Vec3 endPoint() {return smooth[smooth.length-1];}
    public boolean isSettled() {
        for(int i=1;i<points.length;i++)if(smooth[i].distanceToSqr(previous[i])>1e-6)return false;
        return true;
    }
    public void rest() { System.arraycopy(smooth,0,previous,0,smooth.length); }
    public Vec3 closestPoint(Vec3 target) {
        Vec3 best=smooth[0];double nearest=best.distanceToSqr(target);
        for(int i=1;i<smooth.length;i++) {
            Vec3 start=smooth[i-1],span=smooth[i].subtract(start);
            double size=span.lengthSqr();
            Vec3 point=size<1e-10?start:start.add(span.scale(Math.clamp(target.subtract(start).dot(span)/size,0,1)));
            double distance=point.distanceToSqr(target);
            if(distance<nearest){nearest=distance;best=point;}
        }
        return best;
    }
    public Vec3 tangentAt(Vec3 target) {
        Vec3 tangent=smooth[1].subtract(smooth[0]);double nearest=Double.POSITIVE_INFINITY;
        for(int i=1;i<smooth.length;i++) {
            Vec3 span=smooth[i].subtract(smooth[i-1]);double size=span.lengthSqr();
            Vec3 point=size<1e-10?smooth[i-1]:smooth[i-1].add(span.scale(Math.clamp(target.subtract(smooth[i-1]).dot(span)/size,0,1)));
            double distance=point.distanceToSqr(target);if(distance<nearest && size>1e-10){nearest=distance;tangent=span;}
        }
        return tangent.normalize();
    }
    public void payout(double length) { spacing = Math.max(.02, length / (points.length - 1)); }
    public SegmentedChain(Vec3 start, Vec3 end, int segments, double length) {
        segments = Math.clamp(segments, 2, 96);
        points = new Vec3[segments + 1]; old = points.clone(); previous = points.clone(); smooth = points.clone();
        spacing = length / segments;
        for (int i = 0; i <= segments; i++) points[i] = old[i] = previous[i] = smooth[i] = start.lerp(end, i / (double)segments);
    }
    /** Extend from the existing bottom link so placement does not snap a swinging chain straight. */
    public void sag(Vec3 start,Vec3 end) {
        double length=spacing*(points.length-1),low=0,high=length*.5;
        Vec3 bend=end.subtract(start).horizontalDistanceSqr()<.0625?new Vec3(1,0,0):new Vec3(0,-1,0);
        for(int pass=0;pass<24;pass++) {
            double sag=(low+high)*.5,total=0;Vec3 last=start;
            for(int i=1;i<points.length;i++){double t=i/(double)(points.length-1);Vec3 next=start.lerp(end,t).add(bend.scale(4*sag*t*(1-t)));total+=last.distanceTo(next);last=next;}
            if(total<length)low=sag;else high=sag;
        }
        for(int i=0;i<points.length;i++){double t=i/(double)(points.length-1);points[i]=old[i]=previous[i]=smooth[i]=start.lerp(end,t).add(bend.scale(4*(low+high)*.5*t*(1-t)));}
    }
    public SegmentedChain extended(int segments,double length) {
        Vec3[] shape=rendered(1);
        double oldLength=spacing*(points.length-1);
        SegmentedChain next=new SegmentedChain(shape[0],shape[shape.length-1].add(0,-(length-oldLength),0),segments,length);
        for(int i=0;i<next.points.length;i++) {
            double distance=i*next.spacing,index=distance/spacing;
            int low=Math.min(shape.length-1,(int)index);
            Vec3 point=low==shape.length-1?shape[low].add(0,-Math.max(0,distance-oldLength),0)
                    :shape[low].lerp(shape[low+1],index-low);
            next.points[i]=next.old[i]=next.previous[i]=next.smooth[i]=point;
        }
        return next;
    }
    public void step(Vec3 start, Vec3 end, boolean pinEnd, BiFunction<Vec3, Vec3, Vec3> collision,
                     java.util.List<Vec3> bodies) {
        System.arraycopy(smooth, 0, previous, 0, points.length);
        for (int substep = 0; substep < 3; substep++) {
            points[0] = old[0] = start;
            if (pinEnd) points[points.length - 1] = old[points.length - 1] = end;
            for (int i = 1; i < points.length - (pinEnd ? 1 : 0); i++) {
                Vec3 current = points[i];
                Vec3 velocity = current.subtract(old[i]).scale(.90);
                if (velocity.lengthSqr() > .25) velocity = velocity.normalize().scale(.5);
                Vec3 next = current.add(velocity).add(0, -.08 / 9, 0);
                for (Vec3 body : bodies) {
                    Vec3 delta = next.subtract(body);
                    double distance = delta.length();
                    if (distance < .45 && distance > .0001) next = next.add(delta.scale((.45 - distance) / distance * .8));
                    else if (distance <= .0001) next = next.add(.36, 0, 0);
                }
                old[i] = current; points[i] = collision.apply(current, next);
            }
            for (int pass = 0; pass < 12; pass++) {
                for (int sweep = 0; sweep < points.length - 1; sweep++) {
                    int i = (pass & 1) == 0 ? sweep : points.length - 2 - sweep;
                    Vec3 delta = points[i + 1].subtract(points[i]);
                    double distance = delta.length();
                    if (distance < .000001) continue;
                    Vec3 correction = delta.scale((distance - spacing) / distance);
                    boolean a = i == 0, b = pinEnd && i + 1 == points.length - 1;
                    if (!a) points[i] = points[i].add(correction.scale(b ? 1 : .5));
                    if (!b) points[i + 1] = points[i + 1].subtract(correction.scale(a ? 1 : .5));
                }
            }
            for (int i = 1; i < points.length - (pinEnd ? 1 : 0); i++) {
                if (!pinEnd) {
                    // A hanging chain has one fixed end: propagate its hard length limit from that end.
                    // This avoids accumulated stretch on long chains without thousands of relaxation passes.
                    Vec3 span = points[i].subtract(points[i - 1]);
                    if (span.lengthSqr() > spacing * spacing)
                        points[i] = points[i - 1].add(span.normalize().scale(spacing));
                }
                Vec3 corrected = collision.apply(old[i], points[i]);
                if (corrected.distanceToSqr(points[i]) > .000001) old[i] = corrected.lerp(old[i], .15);
                points[i] = corrected;
            }
        }
        smoothRender(pinEnd);
    }
    private void smoothRender(boolean pinEnd) {
        for (int i=0;i<points.length;i++) {
            if (i==0 || pinEnd && i==points.length-1 || smooth[i].distanceToSqr(points[i])>16) smooth[i]=points[i];
            else if (smooth[i].distanceToSqr(points[i])>1e-6) smooth[i]=smooth[i].lerp(points[i], .45);
        }
    }
    public Vec3[] rendered(double partial) {
        Vec3[] result = new Vec3[points.length];
        for (int i = 0; i < points.length; i++) result[i] = previous[i].lerp(smooth[i], Math.clamp(partial, 0, 1));
        return result;
    }
    public boolean near(Vec3 body, double radius) {
        for (Vec3 point : points) if (point.distanceToSqr(body) < radius * radius) return true;
        return false;
    }
}

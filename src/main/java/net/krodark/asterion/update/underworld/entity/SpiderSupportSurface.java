package net.krodark.asterion.update.underworld.entity;

import net.krodark.asterion.entity.BugSurfaces;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Spider-only outer support envelope. Bridges stair recesses, never tunnels through blocks. */
public final class SpiderSupportSurface {
    public record Plane(Vec3 outward, double offset, Vec3 axisU, Vec3 axisV) {
        public double distance(Vec3 point) { return point.dot(outward)-offset; }
        public Vec3 tangent(Vec3 direction) { return direction.subtract(outward.scale(direction.dot(outward))); }
        public Vec3 point(Vec3 center) { return center.subtract(outward.scale(distance(center))); }
        public double clearance(AABB box) {
            return Math.abs(outward.x)*box.getXsize()*.5 + Math.abs(outward.y)*box.getYsize()*.5
                    + Math.abs(outward.z)*box.getZsize()*.5 + .025;
        }
        public Vec3 correction(AABB box, Vec3 motion) {
            return outward.scale(clearance(box)-distance(box.getCenter().add(motion)));
        }
    }
    private SpiderSupportSurface() { }
    /** Visual support frame from a 3x3 neighborhood; physical grip stays exact. */
    public static Vec3 neighborhoodNormal(List<AABB> blocks,AABB body,Direction face) {
        Vec3 normal=face.getUnitVec3();
        Vec3 u=face.getAxis()==Direction.Axis.X?new Vec3(0,0,1):new Vec3(1,0,0);
        Vec3 v=normal.cross(u),sum=normal.scale(2);
        int sameFace=0;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++) {
            AABB sample=body.move(u.scale(x).add(v.scale(y)));
            if(contact(blocks,sample,face)!=null)sameFace++;
            if(x==0 && y==0)continue;
            // Samples are for a broad support frame, never collision permission.
            Direction nearby=SpiderSurfaceRoute.support(blocks,sample,null,body.getCenter());
            if(nearby!=null && nearby!=face.getOpposite())sum=sum.add(nearby.getUnitVec3());
        }
        // Do not tilt a genuinely continuous plane toward neighboring block
        // seams merely because a diagonal sample is closer to a cube's edge.
        return sameFace==9?normal:sum.normalize();
    }
    public static Plane find(BlockGetter level, AABB body, Direction face) {
        return fit(BugSurfaces.collectCollision(level,body.inflate(2.3).expandTowards(face.getUnitVec3().scale(2))),body,face);
    }
    public static Plane contact(BlockGetter level,AABB body,Direction face) {
        return contact(BugSurfaces.collectCollision(level,body.inflate(.08).expandTowards(face.getUnitVec3().scale(.65))),body,face);
    }
    /** A footprint of contact probes also supports flat ceilings and narrow ledges. */
    public static Plane contact(List<AABB> blocks,AABB body,Direction face) {
        Vec3 center=body.getCenter(),up=face.getUnitVec3().scale(-1);
        Vec3 u=face.getAxis()==Direction.Axis.X?new Vec3(0,0,1):new Vec3(1,0,0),v=up.cross(u);
        double radius=(Math.abs(up.x)*body.getXsize()+Math.abs(up.y)*body.getYsize()+Math.abs(up.z)*body.getZsize())*.5;
        List<Double> heights=new ArrayList<>();
        for (double[] probe : new double[][]{{0,0},{-.45,-.45},{-.45,.45},{.45,-.45},{.45,.45}}) {
            Vec3 at=center.add(u.scale(probe[0])).add(v.scale(probe[1]));
            double best=-Double.MAX_VALUE;
            for(AABB block:blocks) {
                if(!covers(block,at,u)||!covers(block,at,v))continue;
                double h=extent(block,up);
                double gap=center.dot(up)-h-radius;
                if(gap>=-.04 && gap<=.65)best=Math.max(best,h);
            }
            if(best!=-Double.MAX_VALUE)heights.add(best);
        }
        if(heights.size()<2)return null;
        double highest=heights.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        if(heights.stream().filter(h->highest-h<.12).count()<2)return null;
        return new Plane(up,highest,u,v);
    }
    public static Plane fit(List<AABB> blocks, AABB body, Direction face) {
        Plane near=fit(blocks,body,face,1);
        return near!=null?near:fit(blocks,body,face,2);
    }
    private static Plane fit(List<AABB> blocks, AABB body, Direction face,double spacing) {
        Vec3 center = body.getCenter(), up = face.getUnitVec3().scale(-1);
        Vec3 u = face.getAxis()==Direction.Axis.X ? new Vec3(0,0,1) : new Vec3(1,0,0);
        Vec3 v = up.cross(u);
        List<double[]> samples = new ArrayList<>();
        List<AABB> supports = new ArrayList<>();
        for (int i=-1;i<=1;i++) for (int j=-1;j<=1;j++) {
            Vec3 at = center.add(u.scale(i*spacing)).add(v.scale(j*spacing));
            AABB best = null;
            double height = -Double.MAX_VALUE;
            for (AABB block : blocks) {
                if (!covers(block,at,u) || !covers(block,at,v)) continue;
                double h = extent(block,up)-center.dot(up);
                if (h > .85 || h < -3.5) continue;
                if (h > height) { best=block; height=h; }
            }
            // A real contact is required across the footprint; don't invent a
            // bridge over missing blocks, openings, or a single isolated pillar.
            if (best == null) return null;
            samples.add(new double[]{i,j,height});
            if (!supports.contains(best)) supports.add(best);
        }
        double a=0,b=0;
        for (double[] p : samples) { a+=p[0]*p[2]; b+=p[1]*p[2]; }
        a/=6*spacing; b/=6*spacing;
        if (a*a+b*b < .025 || a*a+b*b > 2.5 || supports.size()<2) return null;
        // A ramp needs a continuing rise on both sides of the footprint. A
        // single ledge (or a wall beside flat ground) is a corner, not a slope.
        boolean alongU=Math.abs(a)>=Math.abs(b);
        double[] bands=new double[3];
        for(double[] p:samples)bands[(int)p[alongU?0:1]+1]+=p[2]/3;
        double first=bands[1]-bands[0],second=bands[2]-bands[1];
        if(first*second<=0 || Math.min(Math.abs(first),Math.abs(second))<.2
                || Math.abs(first-second)>.25)return null;
        Vec3 outward = up.subtract(u.scale(a)).subtract(v.scale(b)).normalize();
        double offset = -Double.MAX_VALUE;
        for (AABB block : supports) offset=Math.max(offset,extent(block,outward));
        Plane plane = new Plane(outward,offset,planeAxis(outward,u),outward.cross(planeAxis(outward,u)));
        // Reach-limited attachment: a nearby wall must not pull a falling spider
        // sideways across empty space to an unrelated surface.
        double gap = plane.distance(center)-plane.clearance(body);
        return Math.abs(gap) <= .8 ? plane : null;
    }
    private static Vec3 planeAxis(Vec3 normal,Vec3 axis) {
        return axis.subtract(normal.scale(axis.dot(normal))).normalize();
    }
    private static boolean covers(AABB box,Vec3 point,Vec3 axis) {
        double p=point.dot(axis);
        return p>=-extent(box,axis.scale(-1))-.001 && p<=extent(box,axis)+.001;
    }
    private static double extent(AABB b,Vec3 n) {
        return (n.x>=0?b.maxX:b.minX)*n.x+(n.y>=0?b.maxY:b.minY)*n.y+(n.z>=0?b.maxZ:b.minZ)*n.z;
    }
}

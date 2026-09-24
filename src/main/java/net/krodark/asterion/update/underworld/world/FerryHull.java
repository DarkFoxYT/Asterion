package net.krodark.asterion.update.underworld.world;

import net.minecraft.world.phys.Vec3;

/** The three solid deck/hull pieces in charons_ferry.geo.json, in GeckoLib's baked coordinates. */
public final class FerryHull {
    public static final double PIVOT = 17.5 / 16.0, DECK = 19.0 / 16.0, RENDER_OFFSET = .01;
    public static final double RIDER_DROP = .27;
    public record Region(double x, double z, double halfX, double halfZ, double angle) {
        double distance(double px,double pz) {
            double c=Math.cos(angle),s=Math.sin(angle),dx=px-x,dz=pz-z;
            double qx=Math.abs(c*dx-s*dz)-halfX,qz=Math.abs(s*dx+c*dz)-halfZ;
            return Math.hypot(Math.max(qx,0),Math.max(qz,0))+Math.min(Math.max(qx,qz),0);
        }
    }
    public static final Region[] DECK_PIECES={
        cube(-17,-35,34,84,0,false), cube(-17,-45,24,24,-28,true), cube(-17,39,24,24,48,true)};
    public static final Region[] KEEL_PIECES={
        cube(-12.5,-31,25,76,0,false), cube(-11,-39,18,18,-28,true), cube(-11,37,18,18,48,true)};
    private FerryHull() { }
    private static Region cube(double ox,double oz,double sx,double sz,double pivot,boolean rotated) {
        double angle=rotated?Math.PI/4:0,c=Math.cos(angle),s=Math.sin(angle);
        double x=-(ox+sx*.5),z=oz+sz*.5-pivot;
        return new Region((c*x+s*z)/16,(-s*x+c*z+pivot)/16,sx/32,sz/32,angle);
    }
    public static double deckDistance(double x,double z) { return distance(DECK_PIECES,x,z); }
    public static double hullDistance(double x,double y,double z) { return distance(y<11.0/16?KEEL_PIECES:DECK_PIECES,x,z); }
    private static double distance(Region[] regions,double x,double z) {
        double result=Double.POSITIVE_INFINITY;
        for(Region r:regions)result=Math.min(result,r.distance(x,z));
        return result;
    }
    public static Vec3 constrain(double x,double z,double targetX,double targetZ,double radius) {
        int steps=Math.max(1,Math.min(128,(int)Math.ceil(Math.hypot(targetX-x,targetZ-z)/.10)));
        double dx=(targetX-x)/steps,dz=(targetZ-z)/steps;
        // Sliding is resolved along the model axes, without clamping to disconnected rectangles.
        for(int i=0;i<steps;i++) {
            if(deckDistance(x+dx,z+dz)<=-radius) { x+=dx;z+=dz; }
            else {
                if(deckDistance(x+dx,z)<=-radius)x+=dx;
                if(deckDistance(x,z+dz)<=-radius)z+=dz;
            }
        }
        return new Vec3(x,0,z);
    }
    public static Vec3 world(Vec3 local,float yaw,float pitch,float roll) {
        var q=new org.joml.Quaterniond().rotationY(Math.toRadians(180-yaw))
                .rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
        var p=q.transform(new org.joml.Vector3d(local.x,local.y-PIVOT+RENDER_OFFSET,local.z));
        return new Vec3(p.x,p.y+PIVOT,p.z);
    }
    public static Vec3 local(Vec3 world,float yaw,float pitch,float roll) {
        var q=new org.joml.Quaterniond().rotationY(Math.toRadians(180-yaw))
                .rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll)).conjugate();
        var p=q.transform(new org.joml.Vector3d(world.x,world.y-PIVOT,world.z));
        return new Vec3(p.x,p.y+PIVOT-RENDER_OFFSET,p.z);
    }
}

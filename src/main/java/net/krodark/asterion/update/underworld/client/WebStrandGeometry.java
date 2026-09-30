package net.krodark.asterion.update.underworld.client;

import net.minecraft.world.phys.Vec3;

/** A camera-facing ribbon remains visible from above and beside diagonal silk. */
public final class WebStrandGeometry {
    private WebStrandGeometry() { }
    public static double visibility(net.minecraft.world.phys.AABB bounds,Vec3 camera) {
        Vec3 closest=new Vec3(Math.clamp(camera.x,bounds.minX,bounds.maxX),Math.clamp(camera.y,bounds.minY,bounds.maxY),Math.clamp(camera.z,bounds.minZ,bounds.maxZ));
        double t=Math.clamp((closest.distanceTo(camera)-36)/16,0,1);
        return 1-t*t*(3-2*t);
    }
    public static Vec3 ribbon(Vec3 a,Vec3 b,Vec3 camera,double width) {
        Vec3 axis=b.subtract(a).normalize();
        Vec3 view=camera.subtract(a.lerp(b,.5));
        Vec3 side=axis.cross(view);
        if(side.lengthSqr()<1e-8)side=axis.cross(Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0));
        // At distance keep a fraction of a pixel of coverage, bounded so silk
        // never turns into thick ropes. Physical strand width is unchanged.
        double visibleWidth=Math.max(width,Math.min(.055,view.length()*.00065));
        return side.normalize().scale(visibleWidth);
    }
}

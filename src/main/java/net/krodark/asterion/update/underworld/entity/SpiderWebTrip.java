package net.krodark.asterion.update.underworld.entity;

import net.krodark.asterion.update.underworld.WebPatch;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A strand is not usable until the builder has visited both real anchors, in order. */
public final class SpiderWebTrip {
    private final WebPatch patch;
    private int end;
    public SpiderWebTrip(WebPatch patch) { this.patch=patch; }
    public WebPatch patch() { return patch; }
    public boolean attached() { return end>0; }
    public Vec3 goal(AABB body) {
        Vec3 normal=patch.normals().get(end);
        double radius=(Math.abs(normal.x)*body.getXsize()+Math.abs(normal.y)*body.getYsize()+Math.abs(normal.z)*body.getZsize())*.5;
        return patch.anchors().get(end).add(normal.scale(radius+.08)).add(0,-body.getYsize()*.5,0);
    }
    public boolean visit(Vec3 position,AABB body,boolean supported) {
        if(!supported || position.distanceToSqr(goal(body))>.45*.45)return false;
        if(end==0) { end=1;return false; }
        return true;
    }
}

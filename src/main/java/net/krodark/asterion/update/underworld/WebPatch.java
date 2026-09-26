package net.krodark.asterion.update.underworld;
import net.minecraft.world.phys.Vec3;
import java.util.List;
/** Immutable world-derived topology for one anchored silk strand. */
public record WebPatch(long key, List<Vec3> anchors, List<Vec3> normals, List<Edge> edges) {
    public record Edge(int a, int b) { }
    public int pieces(int edge) { Edge e=edges.get(edge); return Math.max(5,Math.min(80,(int)Math.ceil(anchors.get(e.a).distanceTo(anchors.get(e.b))/.38))); }
    public int linkIndex(int edge,double along) { int base=0; for(int i=0;i<edge;i++)base+=pieces(i); return base+Math.min(pieces(edge)-1,(int)Math.floor(Math.clamp(along,0,.999999)*pieces(edge))); }
    public int linkCount(){int count=0;for(int i=0;i<edges.size();i++)count+=pieces(i);return count;}
    /** Shared resting curve, used by both server support and the rendered rope. */
    public Vec3 point(int edge, double along) {
        Edge e = edges.get(edge);
        Vec3 a = anchors.get(e.a), b = anchors.get(e.b);
        double sag = Math.min(1.25, a.distanceTo(b) * .025);
        return a.lerp(b, along).add(0, -4 * along * (1 - along) * sag, 0);
    }
    public boolean anchored(int edge) {
        Edge e = edges.get(edge);
        return normals.get(e.a).lengthSqr() > .001 && normals.get(e.b).lengthSqr() > .001;
    }
    public double nearestAlong(int edge,Vec3 query) {
        Edge e=edges.get(edge);
        Vec3 a=anchors.get(e.a),delta=anchors.get(e.b).subtract(a);
        double t=Math.clamp(query.subtract(a).dot(delta)/Math.max(1e-8,delta.lengthSqr()),0,1);
        double sag=Math.min(1.25,delta.length()*.025);
        for(int i=0;i<4;i++) {
            Vec3 tangent=delta.add(0,-4*(1-2*t)*sag,0);
            t=Math.clamp(t+query.subtract(point(edge,t)).dot(tangent)/Math.max(1e-8,tangent.lengthSqr()),0,1);
        }
        return t;
    }
    public boolean intact(int edge, java.util.BitSet cuts) {
        int first = linkIndex(edge, 0), next = cuts.nextSetBit(first);
        return anchored(edge) && (next < 0 || next >= first + pieces(edge));
    }
}

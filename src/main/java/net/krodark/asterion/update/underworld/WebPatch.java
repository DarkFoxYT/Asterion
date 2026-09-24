package net.krodark.asterion.update.underworld;
import net.minecraft.world.phys.Vec3;
import java.util.List;
/** Immutable world-derived topology for one anchored silk strand. */
public record WebPatch(long key, List<Vec3> anchors, List<Vec3> normals, List<Edge> edges) {
    public record Edge(int a, int b) { }
    public int pieces(int edge) { Edge e=edges.get(edge); return Math.max(5,Math.min(80,(int)Math.ceil(anchors.get(e.a).distanceTo(anchors.get(e.b))/.38))); }
    public int linkIndex(int edge,double along) { int base=0; for(int i=0;i<edge;i++)base+=pieces(i); return base+Math.min(pieces(edge)-1,(int)Math.floor(Math.clamp(along,0,.999999)*pieces(edge))); }
    public int linkCount(){int count=0;for(int i=0;i<edges.size();i++)count+=pieces(i);return count;}
}

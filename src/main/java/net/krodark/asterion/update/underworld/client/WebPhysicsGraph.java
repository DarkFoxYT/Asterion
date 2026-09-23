package net.krodark.asterion.update.underworld.client;
import net.krodark.asterion.update.underworld.WebPatch;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Position-based dynamics rope network: Verlet integration plus iterative distance projection. */
final class WebPhysicsGraph {
    record Link(int a,int b,double rest,int edge,int index) { }
    final WebPatch patch; final List<Vec3> p=new ArrayList<>(), old=new ArrayList<>(); final List<Boolean> pinned=new ArrayList<>(); final List<Link> links=new ArrayList<>();
    WebPhysicsGraph(WebPatch patch) { this.patch=patch; for(Vec3 anchor:patch.anchors()){p.add(anchor);old.add(anchor);pinned.add(true);} build(); }
    private void build(){ int index=0;for(int edge=0;edge<patch.edges().size();edge++){WebPatch.Edge e=patch.edges().get(edge);Vec3 a=patch.anchors().get(e.a()),b=patch.anchors().get(e.b());int previous=e.a();int pieces=patch.pieces(edge);for(int i=1;i<pieces;i++){Vec3 point=a.lerp(b,i/(double)pieces).add(0,-Math.sin(Math.PI*i/pieces)*.12,0);int n=p.size();p.add(point);old.add(point);pinned.add(false);links.add(new Link(previous,n,a.distanceTo(b)/pieces,edge,index++));previous=n;}links.add(new Link(previous,e.b(),a.distanceTo(b)/pieces,edge,index++));} }
    void step(Vec3 player,double radius,java.util.BitSet cuts){
        for(int i=0;i<p.size();i++)if(!pinned.get(i)){Vec3 velocity=p.get(i).subtract(old.get(i)).scale(.985);old.set(i,p.get(i));p.set(i,p.get(i).add(velocity).add(0,-.012,0));}
        if(player!=null)for(int i=0;i<p.size();i++)if(!pinned.get(i)){Vec3 delta=p.get(i).subtract(player);double d=delta.length();if(d<radius&&d>1e-5)p.set(i,p.get(i).add(delta.scale((radius-d)/d*.72)));}
        for(int pass=0;pass<10;pass++)for(Link link:links)if(!cuts.get(link.index))project(link);
        for(int i=0;i<patch.anchors().size();i++){p.set(i,patch.anchors().get(i));old.set(i,patch.anchors().get(i));}
    }
    private void project(Link l){Vec3 delta=p.get(l.b).subtract(p.get(l.a));double d=delta.length();if(d<1e-7)return;Vec3 c=delta.scale((d-l.rest)/d);boolean ap=pinned.get(l.a),bp=pinned.get(l.b);if(!ap&&!bp){p.set(l.a,p.get(l.a).add(c.scale(.5)));p.set(l.b,p.get(l.b).subtract(c.scale(.5)));}else if(!ap)p.set(l.a,p.get(l.a).add(c));else if(!bp)p.set(l.b,p.get(l.b).subtract(c));}
}

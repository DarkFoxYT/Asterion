package net.krodark.asterion.update.underworld.client;
import net.krodark.asterion.update.underworld.WebPatch;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;

/** Position-based dynamics rope network: Verlet integration plus iterative distance projection. */
final class WebPhysicsGraph {
    record Influence(Vec3 position, Vec3 velocity, double radius) { }
    record Link(int a,int b,double rest,int edge,int index) { }
    final WebPatch patch; final AABB bounds; final List<Vec3> p=new ArrayList<>(), old=new ArrayList<>(), renderPrevious=new ArrayList<>(); final List<Boolean> pinned=new ArrayList<>(); final List<Link> links=new ArrayList<>();
    private final boolean stiff;
    WebPhysicsGraph(WebPatch patch) { this.patch=patch;stiff=(patch.key()&1L)==0; AABB box=new AABB(patch.anchors().getFirst(),patch.anchors().getFirst());for(Vec3 anchor:patch.anchors()){p.add(anchor);old.add(anchor);pinned.add(true);box=box.minmax(new AABB(anchor,anchor));}bounds=box.inflate(2);build();renderPrevious.addAll(p); }
    private void build(){ int index=0;for(int edge=0;edge<patch.edges().size();edge++){WebPatch.Edge e=patch.edges().get(edge);Vec3 a=patch.anchors().get(e.a()),b=patch.anchors().get(e.b());int previous=e.a();int pieces=patch.pieces(edge);double rest=a.distanceTo(b)/pieces*(stiff?1.003:1.045);for(int i=1;i<pieces;i++){Vec3 point=a.lerp(b,i/(double)pieces).add(0,-Math.sin(Math.PI*i/pieces)*.035,0);int n=p.size();p.add(point);old.add(point);pinned.add(false);links.add(new Link(previous,n,rest,edge,index++));previous=n;}links.add(new Link(previous,e.b(),rest,edge,index++));} }
    void step(Level level,List<Influence> influences,java.util.BitSet cuts){
        for(int i=0;i<p.size();i++)renderPrevious.set(i,p.get(i));
        // Two small Verlet steps prevent fast moving silk from tunnelling through cave geometry.
        for(int substep=0;substep<2;substep++){
            for(int i=0;i<p.size();i++)if(!pinned.get(i)){Vec3 current=p.get(i),velocity=current.subtract(old.get(i)).scale(stiff?.90:.985),next=current.add(velocity).add(0,stiff?-.0008:-.002,0);old.set(i,current);p.set(i,collide(level,current,next));}
            for(Influence influence:influences)for(int i=0;i<p.size();i++)if(!pinned.get(i)){Vec3 delta=p.get(i).subtract(influence.position());double d=delta.length();if(d<influence.radius()&&d>1e-5){Vec3 push=delta.scale((influence.radius()-d)/d*.7).add(influence.velocity().scale(.5));p.set(i,collide(level,p.get(i),p.get(i).add(push)));}}
            for(int pass=0;pass<(stiff?10:6);pass++)for(Link link:links)if(!cuts.get(link.index))project(link);
            for(int i=0;i<p.size();i++)if(!pinned.get(i))p.set(i,collide(level,old.get(i),p.get(i)));
        }
        for(int i=0;i<patch.anchors().size();i++){p.set(i,patch.anchors().get(i));old.set(i,patch.anchors().get(i));}
    }
    Vec3 rendered(int index, double partialTick) { return renderPrevious.get(index).lerp(p.get(index), partialTick); }
    private static Vec3 collide(Level level,Vec3 from,Vec3 to){
        if(from.distanceToSqr(to)<.16 && level.getBlockState(net.minecraft.core.BlockPos.containing(to)).isAir()
                && level.getBlockState(net.minecraft.core.BlockPos.containing(from)).isAir()) return to;
        BlockHitResult hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
        return hit.getType()==HitResult.Type.MISS?to:hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.012));
    }
    private void project(Link l){Vec3 delta=p.get(l.b).subtract(p.get(l.a));double d=delta.length();if(d<1e-7)return;Vec3 c=delta.scale((d-l.rest)/d);boolean ap=pinned.get(l.a),bp=pinned.get(l.b);if(!ap&&!bp){p.set(l.a,p.get(l.a).add(c.scale(.5)));p.set(l.b,p.get(l.b).subtract(c.scale(.5)));}else if(!ap)p.set(l.a,p.get(l.a).add(c));else if(!bp)p.set(l.b,p.get(l.b).subtract(c));}
}

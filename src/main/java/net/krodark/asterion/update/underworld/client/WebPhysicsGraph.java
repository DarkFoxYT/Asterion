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
    final WebPatch patch; AABB bounds; final List<Vec3> p=new ArrayList<>(), old=new ArrayList<>(), renderPrevious=new ArrayList<>(); final List<Boolean> pinned=new ArrayList<>(); final List<Link> links=new ArrayList<>();
    private final List<Vec3> restPose = new ArrayList<>();
    private final java.util.BitSet loose = new java.util.BitSet();
    private final java.util.BitSet knownCuts = new java.util.BitSet();
    private final boolean stiff;
    WebPhysicsGraph(WebPatch patch) { this.patch=patch;stiff=(patch.key()&3L)!=0; AABB box=new AABB(patch.anchors().getFirst(),patch.anchors().getFirst());for(int i=0;i<patch.anchors().size();i++){Vec3 anchor=patch.anchors().get(i);p.add(anchor);old.add(anchor);pinned.add(patch.normals().get(i).lengthSqr()>.001);box=box.minmax(new AABB(anchor,anchor));}bounds=box.inflate(2);build();renderPrevious.addAll(p); }
    private void build() {
        int index = 0;
        for (int edge = 0; edge < patch.edges().size(); edge++) {
            WebPatch.Edge e = patch.edges().get(edge);
            int previous = e.a(), pieces = patch.pieces(edge);
            for (int i = 1; i <= pieces; i++) {
                Vec3 point = patch.point(edge, i / (double) pieces);
                int next = i == pieces ? e.b() : p.size();
                if (i < pieces) { p.add(point); old.add(point); pinned.add(false); }
                links.add(new Link(previous, next, p.get(previous).distanceTo(point), edge, index++));
                previous = next;
            }
        }
        restPose.addAll(p);
    }
    void step(Level level,List<Influence> influences,java.util.BitSet cuts){
        // Removing the supporting block also releases its rope endpoint.
        for (int i=0;i<patch.anchors().size();i++) if (pinned.get(i)) {
            Vec3 block = patch.anchors().get(i).subtract(patch.normals().get(i).scale(.04));
            if (level.getChunkSource().hasChunk(net.minecraft.core.BlockPos.containing(block).getX()>>4,net.minecraft.core.BlockPos.containing(block).getZ()>>4)
                    && level.getBlockState(net.minecraft.core.BlockPos.containing(block)).getCollisionShape(level,net.minecraft.core.BlockPos.containing(block)).isEmpty()) pinned.set(i,false);
        }
        step(influences,cuts,(from,to)->collide(level,from,to));
    }
    void step(List<Influence> influences,java.util.BitSet cuts,java.util.function.BiFunction<Vec3,Vec3,Vec3> collision) {
        loose.clear();
        for (Link link : links) {
            var edge = patch.edges().get(link.edge());
            if (!patch.intact(link.edge(),cuts) || !pinned.get(edge.a()) || !pinned.get(edge.b())) {
                loose.set(link.a()); loose.set(link.b());
            }
        }
        for(Link link:links)if(cuts.get(link.index())&&!knownCuts.get(link.index())){
            knownCuts.set(link.index());
            snap(link);
        }
        for(int i=0;i<p.size();i++)renderPrevious.set(i,p.get(i));
        // Two small Verlet steps prevent fast moving silk from tunnelling through cave geometry.
        for(int substep=0;substep<2;substep++){
            for(int i=0;i<p.size();i++)if(!pinned.get(i)){
                Vec3 current=p.get(i),velocity=current.subtract(old.get(i)).scale(loose.get(i)?.995:.94);
                // Half-tick acceleration: a released strand falls at normal world gravity.
                Vec3 force=loose.get(i)?new Vec3(0,-.02,0):restPose.get(i).subtract(current).scale(.18);
                Vec3 next=current.add(velocity).add(force);old.set(i,current);p.set(i,collision.apply(current,next));
            }
            for(Influence influence:influences)for(int i=0;i<p.size();i++)if(!pinned.get(i)){Vec3 delta=p.get(i).subtract(influence.position());double d=delta.length();if(d<influence.radius()&&d>1e-5){Vec3 push=delta.scale((influence.radius()-d)/d*.7).add(influence.velocity().scale(.5));p.set(i,collision.apply(p.get(i),p.get(i).add(push)));}}
            for(int pass=0;pass<(stiff?10:6);pass++)for(Link link:links)if(!cuts.get(link.index))project(link);
            for(int i=0;i<p.size();i++)if(!pinned.get(i)) {
                // Keep tensioned silk within the server's interaction envelope.
                Vec3 delta=p.get(i).subtract(restPose.get(i));
                if(!loose.get(i) && delta.lengthSqr()>.36)p.set(i,restPose.get(i).add(delta.normalize().scale(.6)));
                p.set(i,collision.apply(old.get(i),p.get(i)));
            }
        }
        for(int i=0;i<patch.anchors().size();i++)if(pinned.get(i)){p.set(i,patch.anchors().get(i));old.set(i,patch.anchors().get(i));}
        bounds = new AABB(p.getFirst(),p.getFirst());
        for(Vec3 point:p) bounds=bounds.minmax(new AABB(point,point));
        bounds=bounds.inflate(.25);
    }
    Vec3 rendered(int index, double partialTick) { return renderPrevious.get(index).lerp(p.get(index), partialTick); }
    private void snap(Link link) {
        Vec3 tangent = p.get(link.b).subtract(p.get(link.a));
        if (tangent.lengthSqr() < 1e-6) return;
        tangent = tangent.normalize();
        Vec3 sideways = tangent.cross(new Vec3(0, 1, 0));
        if (sideways.lengthSqr() < 1e-5) sideways = new Vec3(1, 0, 0);
        sideways = sideways.normalize();
        boolean taut = stiff && patch.anchors().getFirst().distanceTo(patch.anchors().getLast()) > 7
                && (patch.key() & 3L) == 1L;
        double kick = taut ? .48 : .11;
        impulse(link.a, tangent.scale(-kick).add(sideways.scale(kick * .55)));
        impulse(link.b, tangent.scale(kick).add(sideways.scale(-kick * .55)));
    }
    private void impulse(int index, Vec3 velocity) {
        if (!pinned.get(index)) old.set(index, p.get(index).subtract(velocity));
    }
    private static Vec3 collide(Level level,Vec3 from,Vec3 to){
        if(from.distanceToSqr(to)<.16 && level.getBlockState(net.minecraft.core.BlockPos.containing(to)).isAir()
                && level.getBlockState(net.minecraft.core.BlockPos.containing(from)).isAir()) return to;
        BlockHitResult hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
        return hit.getType()==HitResult.Type.MISS?to:hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.012));
    }
    private void project(Link l){Vec3 delta=p.get(l.b).subtract(p.get(l.a));double d=delta.length();if(d<1e-7)return;Vec3 c=delta.scale((d-l.rest)/d);boolean ap=pinned.get(l.a),bp=pinned.get(l.b);if(!ap&&!bp){p.set(l.a,p.get(l.a).add(c.scale(.5)));p.set(l.b,p.get(l.b).subtract(c.scale(.5)));}else if(!ap)p.set(l.a,p.get(l.a).add(c));else if(!bp)p.set(l.b,p.get(l.b).subtract(c));}
}

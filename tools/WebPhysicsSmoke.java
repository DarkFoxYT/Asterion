package net.krodark.asterion.update.underworld.client;

import net.krodark.asterion.update.underworld.WebPatch;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Checks the production solver, including long strands and loss of both anchors. */
public final class WebPhysicsSmoke {
    public static void main(String[] args) {
        int cases=0;
        var visibilityBounds=new net.minecraft.world.phys.AABB(0,0,0,2,2,2);
        if(WebStrandGeometry.visibility(visibilityBounds,new Vec3(54,1,1))!=0
                || WebStrandGeometry.visibility(visibilityBounds,new Vec3(20,1,1))!=1)
            throw new AssertionError("Web preloading boundary remains visible or nearby silk fades");
        double previous=0;
        for(double distance=52;distance>=0;distance-=.1) {
            double current=WebStrandGeometry.visibility(visibilityBounds,new Vec3(distance+2,1,1));
            if(current<previous || current-previous>.01)throw new AssertionError("Web entry still pops abruptly");
            previous=current;
        }
        var span=new WebPatch(123,List.of(new Vec3(0,20,0),new Vec3(120,20,0)),
                List.of(new Vec3(1,0,0),new Vec3(-1,0,0)),List.of(new WebPatch.Edge(0,1)));
        if(!span.near(span.point(0,.5),10) || span.near(new Vec3(60,100,0),10))
            throw new AssertionError("Long strand visibility still depends on its first anchor");
        var directions=net.krodark.asterion.update.underworld.WebPatchGenerator.spinDirections();
        if(directions.size()!=26 || directions.stream().filter(v->Math.abs(v.x)+Math.abs(v.y)+Math.abs(v.z)>1.1).count()!=20)
            throw new AssertionError("Missing diagonal or vertical web placement directions");
        for(Vec3 end:List.of(new Vec3(48,0,0),new Vec3(0,48,0),new Vec3(48,24,36))) {
            Vec3 axis=end.normalize();
            for(Vec3 camera:List.of(new Vec3(20,60,30),new Vec3(40,0,100),end.scale(2),new Vec3(500,200,300))) {
                Vec3 side=WebStrandGeometry.ribbon(Vec3.ZERO,end,camera,.012);
                if(!Double.isFinite(side.lengthSqr()) || side.length()<.01199 || side.length()>.05501
                        || Math.abs(side.dot(axis))>1e-8)
                    throw new AssertionError("Strand ribbon collapses or loses its axis on a diagonal/view angle");
                Vec3 view=camera.subtract(end.scale(.5));
                if(axis.cross(view).lengthSqr()>1e-8 && Math.abs(side.normalize().dot(view.normalize()))>1e-8)
                    throw new AssertionError("Strand ribbon turns edge-on to the camera");
            }
        }
        for(int length:new int[]{4,16,48})for(long key:new long[]{0,1,2,3}) {
            WebPatch patch=new WebPatch(key,List.of(new Vec3(0,20,0),new Vec3(length,20,0)),
                    List.of(new Vec3(1,0,0),new Vec3(-1,0,0)),List.of(new WebPatch.Edge(0,1)));
            WebPhysicsGraph graph=new WebPhysicsGraph(patch);
            for(double t:new double[]{0,.1,.5,.8,1})
                if(Math.abs(patch.nearestAlong(0,patch.point(0,t))-t)>.0001)throw new AssertionError("Silk support projection drift");
            BitSet cuts=new BitSet();
            for(int tick=0;tick<100;tick++)graph.step(List.of(),cuts,(from,to)->to);
            for(var link:graph.links)if(Math.abs(graph.p.get(link.a()).distanceTo(graph.p.get(link.b()))-link.rest())>1e-5)
                throw new AssertionError("Resting silk drifted");
            int sever=patch.pieces(0)/2;
            int tip=graph.links.get(sever).a();
            double before=graph.p.get(tip).y;
            cuts.set(sever);
            if(patch.intact(0,cuts))throw new AssertionError("Cut span still supports spiders");
            for(int tick=0;tick<10;tick++)graph.step(List.of(),cuts,(from,to)->to);
            double fallen=before-graph.p.get(tip).y;
            if(fallen<.7)throw new AssertionError("Slow cut rope: length="+length+" fall="+fallen);
            if(graph.p.get(0).distanceToSqr(patch.anchors().get(0))>1e-9)throw new AssertionError("Pinned endpoint moved");
            graph.pinned.replaceAll(p->false);
            before=graph.p.get(0).y;
            for(int tick=0;tick<20;tick++)graph.step(List.of(),cuts,(from,to)->to);
            if(before-graph.p.get(0).y<3)throw new AssertionError("Detached rope floats");
            for(Vec3 p:graph.p)if(!Double.isFinite(p.lengthSqr()) || !graph.bounds.contains(p))throw new AssertionError("Invalid rope/bounds");
            cases++;
        }
        System.out.println("PASS "+cases+" silk cases: resting support, cuts, gravity, anchors, and falling bounds");
    }
}

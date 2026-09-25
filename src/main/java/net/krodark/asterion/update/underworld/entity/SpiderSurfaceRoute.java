package net.krodark.asterion.update.underworld.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded local search around ledges; every swept body stays clear and within leg reach. */
public final class SpiderSurfaceRoute {
    public record Point(Vec3 center,Direction face) { }
    private record Cell(int x,int y,int z) { }
    private record Node(Cell cell,double cost,double score) { }
    private SpiderSurfaceRoute() { }

    public static Direction support(List<AABB> blocks,AABB body,Direction previous) {
        Vec3 p=body.getCenter();
        double best=.45*.45;
        Direction face=null;
        for(AABB block:blocks) {
            double dx=Math.max(block.minX-body.maxX,Math.max(body.minX-block.maxX,0));
            double dy=Math.max(block.minY-body.maxY,Math.max(body.minY-block.maxY,0));
            double dz=Math.max(block.minZ-body.maxZ,Math.max(body.minZ-block.maxZ,0));
            double distance=dx*dx+dy*dy+dz*dz;
            Vec3 to=new Vec3(Math.clamp(p.x,block.minX,block.maxX)-p.x,
                    Math.clamp(p.y,block.minY,block.maxY)-p.y,
                    Math.clamp(p.z,block.minZ,block.maxZ)-p.z);
            Direction candidate=Direction.UP;
            double alignment=-Double.MAX_VALUE;
            for(Direction d:Direction.values())if(to.dot(d.getUnitVec3())>alignment) {
                alignment=to.dot(d.getUnitVec3());candidate=d;
            }
            // Keep the previous face at a corner until another is clearly closer.
            double score=distance+(candidate==previous?0:.025);
            if(score<best) { best=score;face=candidate; }
        }
        return face;
    }
    public static boolean clear(List<AABB> blocks,AABB body,Vec3 step) {
        AABB swept=body.expandTowards(step).deflate(.001);
        return blocks.stream().noneMatch(swept::intersects);
    }
    public static List<Point> find(List<AABB> blocks,AABB body,Direction face,Vec3 target) {
        Vec3 origin=body.getCenter();
        Vec3 toward=target.subtract(origin);
        if(toward.length()>4)toward=toward.normalize().scale(4);
        Vec3 goal=origin.add(toward);
        Cell start=new Cell(0,0,0),best=start;
        Map<Cell,Double> costs=new HashMap<>();
        Map<Cell,Cell> parents=new HashMap<>();
        Map<Cell,Direction> faces=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        costs.put(start,0.0);faces.put(start,face);
        double initial=origin.distanceTo(goal),nearest=initial;
        open.add(new Node(start,0,initial));
        int visited=0;
        while(!open.isEmpty() && visited++<1800) {
            Node node=open.remove();Cell c=node.cell;
            if(node.cost>costs.get(c)+1e-6)continue;
            Vec3 offset=new Vec3(c.x*.35,c.y*.35,c.z*.35);
            double remaining=origin.add(offset).distanceTo(goal);
            if(remaining<nearest) { nearest=remaining;best=c; }
            if(remaining<.4)break;
            for(Direction direction:Direction.values()) {
                Cell next=new Cell(c.x+direction.getStepX(),c.y+direction.getStepY(),c.z+direction.getStepZ());
                if(Math.abs(next.x)>13 || Math.abs(next.y)>13 || Math.abs(next.z)>13)continue;
                Vec3 step=direction.getUnitVec3().scale(.35);
                AABB current=body.move(offset),moved=current.move(step);
                if(!clear(blocks,current,step))continue;
                Direction support=support(blocks,moved,faces.get(c));
                if(support==null)continue;
                double cost=node.cost+.35+(support==faces.get(c)?0:.08);
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                costs.put(next,cost);parents.put(next,c);faces.put(next,support);
                open.add(new Node(next,cost,cost+moved.getCenter().distanceTo(goal)));
            }
        }
        if(initial-nearest<.5)return List.of();
        LinkedList<Point> route=new LinkedList<>();
        for(Cell c=best;!c.equals(start);c=parents.get(c))
            route.addFirst(new Point(origin.add(c.x*.35,c.y*.35,c.z*.35),faces.get(c)));
        return List.copyOf(route);
    }
}

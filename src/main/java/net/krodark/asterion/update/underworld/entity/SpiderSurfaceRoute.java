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
        return support(blocks,body,previous,body.getCenter());
    }
    static Direction support(List<AABB> blocks,AABB body,Direction previous,Vec3 p) {
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
        return find(blocks,body,face,target,.35,13,1800);
    }
    /** Longer surface navigation, including approach to a wall before climbing it. */
    public static List<Point> navigate(List<AABB> blocks,AABB body,Direction face,Vec3 target) {
        return find(blocks,body,face,target,.5,24,1600);
    }
    private static List<Point> find(List<AABB> blocks,AABB body,Direction face,Vec3 target,
                                    double spacing,int radius,int budget) {
        Vec3 origin=body.getCenter();
        Vec3 goal=target;
        // Index collision shapes once, instead of scanning an entire cave for
        // every successor. Each bucket contains only nearby collision shapes.
        Map<Cell,List<AABB>> buckets=new HashMap<>();
        for(AABB block:blocks) {
            for(int x=(int)Math.floor(block.minX/2);x<=(int)Math.floor(block.maxX/2);x++)
                for(int y=(int)Math.floor(block.minY/2);y<=(int)Math.floor(block.maxY/2);y++)
                    for(int z=(int)Math.floor(block.minZ/2);z<=(int)Math.floor(block.maxZ/2);z++)
                        buckets.computeIfAbsent(new Cell(x,y,z),ignored->new ArrayList<>()).add(block);
        }
        Cell start=new Cell(0,0,0),best=start;
        Map<Cell,Double> costs=new HashMap<>();
        Map<Cell,Cell> parents=new HashMap<>();
        Map<Cell,Direction> faces=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        costs.put(start,0.0);faces.put(start,face);
        double initial=origin.distanceTo(goal),nearest=initial;
        open.add(new Node(start,0,initial));
        int visited=0;
        while(!open.isEmpty() && visited++<budget) {
            Node node=open.remove();Cell c=node.cell;
            if(node.cost>costs.get(c)+1e-6)continue;
            Vec3 offset=new Vec3(c.x*spacing,c.y*spacing,c.z*spacing);
            double remaining=origin.add(offset).distanceTo(goal);
            if(remaining<nearest) { nearest=remaining;best=c; }
            if(remaining<.4)break;
            AABB current=body.move(offset);
            AABB bounds=current.inflate(spacing+.5);
            Set<AABB> nearbySet=new HashSet<>();
            for(int x=(int)Math.floor(bounds.minX/2);x<=(int)Math.floor(bounds.maxX/2);x++)
                for(int y=(int)Math.floor(bounds.minY/2);y<=(int)Math.floor(bounds.maxY/2);y++)
                    for(int z=(int)Math.floor(bounds.minZ/2);z<=(int)Math.floor(bounds.maxZ/2);z++) {
                        var bucket=buckets.get(new Cell(x,y,z));
                        if(bucket!=null)nearbySet.addAll(bucket);
                    }
            List<AABB> nearby=List.copyOf(nearbySet);
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dy==0 && dz==0)continue;
                Cell next=new Cell(c.x+dx,c.y+dy,c.z+dz);
                if(Math.abs(next.x)>radius || Math.abs(next.y)>radius || Math.abs(next.z)>radius)continue;
                Vec3 step=new Vec3(dx*spacing,dy*spacing,dz*spacing);
                double cost=node.cost+step.length();
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                AABB moved=current.move(step);
                if(!clear(nearby,current,step))continue;
                Direction support=support(nearby,moved,faces.get(c));
                if(support==null)continue;
                // Endpoints alone must not allow diagonal hops across empty air.
                if(support(nearby,current.move(step.scale(.5)),faces.get(c))==null)continue;
                cost+=support==faces.get(c)?0:.08;
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                costs.put(next,cost);parents.put(next,c);faces.put(next,support);
                open.add(new Node(next,cost,cost+1.3*moved.getCenter().distanceTo(goal)));
            }
        }
        if(initial-nearest<.5)return List.of();
        LinkedList<Point> route=new LinkedList<>();
        for(Cell c=best;!c.equals(start);c=parents.get(c))
            route.addFirst(new Point(origin.add(c.x*spacing,c.y*spacing,c.z*spacing),faces.get(c)));
        return smooth(blocks,body,route);
    }
    /** Safe string-pulling across the leg-reach envelope, not voxel stair jumps. */
    public static List<Point> smooth(List<AABB> blocks,AABB body,List<Point> raw) {
        List<Point> result=new ArrayList<>();
        int index=0;
        while(index<raw.size()) {
            AABB bounds=body.inflate(2.5);
            List<AABB> nearby=blocks.stream().filter(bounds::intersects).toList();
            int last=index;
            for(int candidate=index+1;candidate<Math.min(raw.size(),index+7);candidate++) {
                Vec3 step=raw.get(candidate).center().subtract(body.getCenter());
                if(step.length()>2 || !clear(nearby,body,step))break;
                boolean supported=true;
                int samples=Math.max(2,(int)Math.ceil(step.length()/.1));
                for(int sample=1;sample<=samples;sample++)
                    if(support(nearby,body.move(step.scale(sample/(double)samples)),raw.get(candidate).face())==null) { supported=false;break; }
                if(!supported)break;
                last=candidate;
            }
            Point point=raw.get(last);result.add(point);
            body=body.move(point.center().subtract(body.getCenter()));index=last+1;
        }
        return List.copyOf(result);
    }
}

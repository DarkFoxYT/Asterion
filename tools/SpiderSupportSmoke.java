package net.krodark.asterion.update.underworld.client;

import net.krodark.asterion.update.underworld.entity.SpiderSupportSurface;
import net.krodark.asterion.update.underworld.entity.SpiderSurfaceRoute;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** The illustrated stair profile, rotated onto floors, ceilings and diagonal walls. */
public final class SpiderSupportSmoke {
    public static void main(String[] args) {
        int checks=0;
        for(Direction face:Direction.values()) {
            Quaternionf rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),
                    new Vector3f(-face.getStepX(),-face.getStepY(),-face.getStepZ()));
            List<AABB> stairs=new ArrayList<>();
            for(int i=-5;i<=5;i++) stairs.add(rotate(new AABB(i,i-1,-4,i+1,i,4),rotation));
            AABB body=rotate(new AABB(-.475,1.05,-.725,.975,2.35,.725),rotation);
            var plane=SpiderSupportSurface.fit(stairs,body,face);
            require(plane!=null,"Missing diagonal support on "+face);
            Vec3 up=rotate(new Vec3(0,1,0),rotation);
            require(plane.outward().dot(up)>.65 && plane.outward().dot(up)<.8,"Did not fit 45-degree slope");
            Vec3 advance=plane.tangent(rotate(new Vec3(.25,0,0),rotation));
            Vec3 correction=plane.correction(body,advance);
            AABB supported=body.move(correction).move(advance);
            for(AABB block:stairs) require(!supported.intersects(block),"Envelope penetrates original blocks on "+face);
            require(Math.abs(plane.distance(supported.getCenter())-plane.clearance(supported))<1e-6,"Body not on envelope");
            for (double phase : new double[]{.05,.25,.49,.75,.99}) {
                AABB moving=rotate(new AABB(phase-.725,phase+.8,-.725,phase+.725,phase+2.1,.725),rotation);
                // Match the real collector's spatial bounds, including the lower
                // step just before the spider crosses a voxel boundary.
                AABB bounds=moving.inflate(1.3).expandTowards(face.getUnitVec3().scale(2));
                var local=stairs.stream().filter(bounds::intersects).toList();
                var fitted=SpiderSupportSurface.fit(local,moving,face);
                require(fitted!=null,"Support dropped at voxel phase "+phase+" on "+face);
                require(fitted.outward().dot(plane.outward())>.9999,"Normal twitched at voxel boundary");
            }
            // A nearby flat plane must use ordinary collision, and a missing center
            // column must not become a magical support across an opening.
            List<AABB> flat=List.of(rotate(new AABB(-5,-1,-5,5,0,5),rotation));
            require(SpiderSupportSurface.fit(flat,body,face)==null,"Flat ground became a slope");
            var ledge=List.of(flat.getFirst(),rotate(new AABB(0,0,-5,5,1,5),rotation));
            require(SpiderSupportSurface.fit(ledge,body,face)==null,"Single step became a ramp on "+face);
            List<AABB> terraces=new ArrayList<>();
            for(int i=-5;i<=5;i++)terraces.add(rotate(new AABB(i*2,i-1,-5,i*2+2,i,5),rotation));
            var terrace=SpiderSupportSurface.fit(terraces,body,face);
            require(terrace!=null,"Wide treads lost slope orientation on "+face);
            require(terrace.outward().dot(up)>.85 && terrace.outward().dot(up)<.95,"Wide tread normal is flat");
            AABB settled=body.move(terrace.correction(body,Vec3.ZERO));
            for(AABB block:terraces)require(!settled.intersects(block),"Wide tread support penetrates blocks");
            AABB resting=rotate(new AABB(-.725,.24,-.725,.725,1.54,.725),rotation);
            for(int tick=0;tick<120;tick++) {
                var contact=SpiderSupportSurface.contact(flat,resting,face);
                require(contact!=null,"Lost flat adhesion on "+face);
                Vec3 settle=contact.correction(resting,Vec3.ZERO);
                resting=resting.move(settle);
                require(!resting.intersects(flat.getFirst()),"Adhesion penetrated support");
                if(tick>0)require(settle.length()<1e-6,"Stationary support jitter on "+face);
            }
            require(SpiderSupportSurface.contact(List.of(),resting,face)==null,"Removed support still attached");
            stairs.remove(5);
            require(SpiderSupportSurface.fit(stairs,body,face)==null,"Fit bridged a missing center column");
            checks++;
        }
        List<AABB> ceiling=List.of(new AABB(-5,3,-5,0,6,5),new AABB(0,5,-5,5,6,5));
        AABB hanging=new AABB(-1.725,1.675,-.725,-.275,2.975,.725);
        Vec3 target=new Vec3(2,4.325,0);
        var route=SpiderSurfaceRoute.find(ceiling,hanging,Direction.UP,target);
        require(!route.isEmpty(),"No route around raised ceiling ledge");
        AABB cursor=hanging;
        boolean wall=false;
        for(var point:route) {
            Vec3 step=point.center().subtract(cursor.getCenter());
            require(SpiderSurfaceRoute.clear(ceiling,cursor,step),"Route clipped ledge");
            cursor=cursor.move(step);
            require(SpiderSurfaceRoute.support(ceiling,cursor,point.face())!=null,"Route lost leg support");
            wall|=point.face().getAxis().isHorizontal();
        }
        require(wall && cursor.getCenter().y>3.8,"Route never climbed the riser");
        require(cursor.getCenter().distanceTo(target)<.5,"Route did not reach higher ceiling");
        require(SpiderSurfaceRoute.find(List.of(),hanging,Direction.UP,target).isEmpty(),"Route crossed unsupported air");
        require(!SpiderSurfaceRoute.clear(List.of(new AABB(-2,1,-1,3,5,1)),hanging,new Vec3(.35,0,0)),"Blocked sweep accepted");
        for(Direction face:Direction.Plane.HORIZONTAL) {
            Quaternionf q=new Quaternionf().rotationTo(new Vector3f(0,1,0),
                    new Vector3f(face.getStepX(),face.getStepY(),face.getStepZ()));
            var blocks=ceiling.stream().map(block->rotate(block,q)).toList();
            AABB body=rotate(hanging,q);
            Vec3 goal=rotate(target,q);
            var wallRoute=SpiderSurfaceRoute.find(blocks,body,face,goal);
            require(!wallRoute.isEmpty(),"No wall ledge route on "+face);
            for(var point:wallRoute) {
                Vec3 step=point.center().subtract(body.getCenter());
                require(SpiderSurfaceRoute.clear(blocks,body,step),"Wall route intersects blocks");
                body=body.move(step);
                require(SpiderSurfaceRoute.support(blocks,body,point.face())!=null,"Wall route loses support");
            }
            require(body.getCenter().distanceTo(goal)<.5,"Wall route did not reach destination on "+face);
        }
        System.out.println("Spider support: "+checks+" orientations passed: slopes, isolated ledges, 120-tick flat adhesion, clearance and removal; raised-ceiling route, swept clearance and missing-support checks passed.");
    }
    private static AABB rotate(AABB b,Quaternionf q) {
        AABB result=null;
        for(int i=0;i<8;i++) {
            Vec3 p=rotate(new Vec3((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ),q);
            AABB point=new AABB(p,p); result=result==null?point:result.minmax(point);
        }
        return result;
    }
    private static Vec3 rotate(Vec3 v,Quaternionf q) {
        Vector3f p=q.transform(new Vector3f((float)v.x,(float)v.y,(float)v.z));
        return new Vec3(p.x,p.y,p.z);
    }
    private static void require(boolean test,String message) { if(!test)throw new AssertionError(message); }
}

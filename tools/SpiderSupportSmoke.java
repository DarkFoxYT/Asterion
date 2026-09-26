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
            require(SpiderSupportSurface.neighborhoodNormal(flat,resting,face).dot(face.getUnitVec3())>.99999,
                    "3x3 frame tilted a flat surface on "+face);
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
        List<AABB> room=List.of(new AABB(-14,-1,-14,14,0,14),
                new AABB(5,0,-14,6,9,14),new AABB(-14,9,-14,6,10,14));
        AABB grounded=new AABB(-.725,.025,-.725,.725,1.325,.725);
        Vec3 roofTarget=new Vec3(0,8.325,0);
        long started=System.nanoTime();
        var climb=SpiderSurfaceRoute.navigate(room,grounded,Direction.DOWN,roofTarget);
        require(!climb.isEmpty(),"No floor-to-wall approach for roof directly overhead");
        boolean climbedWall=false,reachedCeiling=false,diagonal=false;
        AABB crawler=grounded;
        for(var point:climb) {
            Vec3 step=point.center().subtract(crawler.getCenter());
            require(SpiderSurfaceRoute.clear(room,crawler,step),"Full ascent clips terrain");
            for(int i=0;i<=10;i++)require(SpiderSurfaceRoute.support(room,crawler.move(step.scale(i/10.0)),point.face())!=null,
                    "Full ascent loses grip between route nodes");
            diagonal|=Math.abs(step.x)>.01 && Math.abs(step.y)>.01;
            crawler=crawler.move(step);
            climbedWall|=point.face()==Direction.EAST;
            reachedCeiling|=point.face()==Direction.UP;
        }
        require(climbedWall && reachedCeiling && crawler.getCenter().distanceTo(roofTarget)<.5,
                "Ascent did not connect floor, wall and ceiling: "+crawler.getCenter());
        require(diagonal,"Surface route did not use diagonal movement");
        double width=net.krodark.asterion.update.underworld.entity.SpiderDimensions.WIDTH;
        double height=net.krodark.asterion.update.underworld.entity.SpiderDimensions.HEIGHT;
        AABB large=new AABB(-width/2,.025,-width/2,width/2,.025+height,width/2);
        Vec3 largeGoal=new Vec3(0,9-height/2-.025,0);
        var largeRoute=SpiderSurfaceRoute.navigate(room,large,Direction.DOWN,largeGoal);
        require(!largeRoute.isEmpty(),"Larger spider cannot plan a ceiling ascent");
        for(var point:largeRoute) {
            Vec3 advance=point.center().subtract(large.getCenter());
            require(SpiderSurfaceRoute.clear(room,large,advance),"Larger spider route clips its body");
            large=large.move(advance);
        }
        require(large.getCenter().distanceTo(largeGoal)<.5,"Larger spider failed to reach ceiling");
        require(SpiderSurfaceRoute.navigate(List.of(room.getFirst(),room.getLast()),grounded,Direction.DOWN,roofTarget).isEmpty(),
                "Roof without connecting wall was treated as reachable");
        // The user's diagrams: a one-block step, and a ceiling lip that must
        // lead back up the exposed outside wall instead of dropping the spider.
        List<AABB> stepBlocks=List.of(new AABB(-8,-2,-5,0,0,5),new AABB(0,-2,-5,8,1,5));
        AABB stepBody=grounded.move(-2,0,0);
        var stepRoute=SpiderSurfaceRoute.navigate(stepBlocks,stepBody,Direction.DOWN,new Vec3(3,1.675,0));
        require(!stepRoute.isEmpty(),"Single step requires a jump");
        for(var point:stepRoute) {
            Vec3 advance=point.center().subtract(stepBody.getCenter());
            require(SpiderSurfaceRoute.clear(stepBlocks,stepBody,advance),"Step envelope intersects blocks");
            stepBody=stepBody.move(advance);
        }
        require(stepBody.getCenter().distanceTo(new Vec3(3,1.675,0))<.5,"Step climb stops below tread");
        List<AABB> lip=List.of(new AABB(0,3,-5,8,8,5));
        AABB under=new AABB(1.275,1.675,-.725,2.725,2.975,.725);
        var wrap=SpiderSurfaceRoute.navigate(lip,under,Direction.UP,new Vec3(-.8,5,0));
        require(!wrap.isEmpty(),"Ceiling lip cannot reach outside wall");
        for(var point:wrap) {
            Vec3 advance=point.center().subtract(under.getCenter());
            require(SpiderSurfaceRoute.clear(lip,under,advance),"Ceiling wrap clips corner");
            for(int i=0;i<=20;i++)require(SpiderSurfaceRoute.support(lip,under.move(advance.scale(i/20.0)),point.face())!=null,"Ceiling wrap loses grip");
            under=under.move(advance);
        }
        require(under.getCenter().distanceTo(new Vec3(-.8,5,0))<.5,"Ceiling wrap never climbed outside wall");
        partialBlocks();
        var ledgeFrame=SpiderSupportSurface.neighborhoodNormal(
                List.of(new AABB(0,-5,-5,1,5,5),new AABB(-1,1.5,0,0,2.5,1)),
                new AABB(-1.5,.175,-.225,-.05,1.475,1.225),Direction.UP);
        require(ledgeFrame.x>.1 && ledgeFrame.y>.1 && Math.abs(ledgeFrame.length()-1)<1e-8,
                "One-block lip dominated the entire body frame: "+ledgeFrame);
        System.out.println("Spider support: "+checks+" orientations, ledges/removal, diagonal floor-wall-ceiling route, single step and outer ceiling wrap passed (route suite "+((System.nanoTime()-started)/1_000_000)+" ms).");
    }
    private static void partialBlocks() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var block:List.of(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE,
                net.minecraft.world.level.block.Blocks.OAK_FENCE,net.minecraft.world.level.block.Blocks.STONE_SLAB,
                net.minecraft.world.level.block.Blocks.OAK_TRAPDOOR,net.minecraft.world.level.block.Blocks.CHEST)) {
            var partial=block.defaultBlockState();
            net.minecraft.world.level.BlockGetter level=new net.minecraft.world.level.BlockGetter() {
                public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) { return null; }
                public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
                    return pos.getY()==-1?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                            :pos.equals(net.minecraft.core.BlockPos.ZERO)?partial:net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                }
                public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) { return getBlockState(pos).getFluidState(); }
                public int getHeight() { return 384; }
                public int getMinY() { return -64; }
            };
            var shapes=net.krodark.asterion.entity.BugSurfaces.collectCollision(level,new AABB(-5,-2,-5,6,4,5));
            for(var shape:partial.getCollisionShape(level,net.minecraft.core.BlockPos.ZERO).toAabbs())
                require(shapes.contains(shape),"Planner omitted actual partial-block shape: "+block);
            double width=net.krodark.asterion.update.underworld.entity.SpiderDimensions.WIDTH;
            double height=net.krodark.asterion.update.underworld.entity.SpiderDimensions.HEIGHT;
            AABB body=new AABB(-2-width/2,.025,.5-width/2,-2+width/2,.025+height,.5+width/2);
            Vec3 goal=new Vec3(3,.025+height/2,.5);
            var route=SpiderSurfaceRoute.navigate(shapes,body,Direction.DOWN,goal);
            require(!route.isEmpty(),"No route around partial block "+block);
            for(var point:route) {
                Vec3 advance=point.center().subtract(body.getCenter());
                require(SpiderSurfaceRoute.clear(shapes,body,advance),"Route intersects partial block "+block);
                body=body.move(advance);
            }
            require(body.getCenter().distanceTo(goal)<.5,"Partial block still traps crawler "+block);
        }
        System.out.println("Actual voxel-shape collection and routes passed: dripstone spikes, fences, slabs, trapdoors, chests.");
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

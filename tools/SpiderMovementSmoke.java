package net.krodark.asterion.update.underworld.client;

import net.krodark.asterion.client.render.entity.SurfaceOrientation;
import net.krodark.asterion.update.underworld.entity.SpiderSurfaceMotion;
import net.krodark.asterion.entity.CentipedeSurfaceProbe;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Regressions for wall reversals, zero-heading ceiling corners and yaw-induced visual twitching. */
public final class SpiderMovementSmoke {
    public static void main(String[] args) {
        int checks = 0;
        require(net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE==1.5F,"Wrong requested size");
        require(!net.krodark.asterion.update.underworld.entity.SpiderBehavior.night(11999)
                && net.krodark.asterion.update.underworld.entity.SpiderBehavior.night(12000),"Day/night boundary");
        require(!net.krodark.asterion.update.underworld.entity.SpiderBehavior.ambush(30,12,12,false,20),"Stationary prey falsely triggered ambush");
        require(net.krodark.asterion.update.underworld.entity.SpiderBehavior.ambush(30,12.2,12,false,20),"Departing prey did not trigger ambush");
        require(net.krodark.asterion.update.underworld.entity.SpiderBehavior.ambush(10,12,12,true,20),"Seen ambush did not trigger");
        require(!net.krodark.asterion.update.underworld.entity.SpiderBehavior.flee(30,60,30)
                && net.krodark.asterion.update.underworld.entity.SpiderBehavior.flee(29,60,1)
                && net.krodark.asterion.update.underworld.entity.SpiderBehavior.flee(50,60,31),"Hurt flee thresholds");
        var patch=new net.krodark.asterion.update.underworld.WebPatch(42,
                List.of(new Vec3(0,0,0),new Vec3(8,0,0)),List.of(new Vec3(0,1,0),new Vec3(0,1,0)),
                List.of(new net.krodark.asterion.update.underworld.WebPatch.Edge(0,1)));
        var trip=new net.krodark.asterion.update.underworld.entity.SpiderWebTrip(patch);
        double w=net.krodark.asterion.update.underworld.entity.SpiderDimensions.WIDTH,h=net.krodark.asterion.update.underworld.entity.SpiderDimensions.HEIGHT;
        AABB body=new AABB(-w/2,0,-w/2,w/2,h,w/2);
        require(!trip.visit(new Vec3(8,.08,0),body,true) && !trip.attached(),"Builder skipped first anchor");
        require(!trip.visit(trip.goal(body),body,false) && !trip.attached(),"Airborne builder bound an anchor");
        require(!trip.visit(trip.goal(body),body,true) && trip.attached(),"First anchor finished entire web");
        require(!trip.visit(new Vec3(4,.08,0),body,true),"Web finished before the trip");
        require(trip.visit(trip.goal(body),body,true),"Completed trip did not finish web");
        for(Direction face:Direction.values()) {
            Vec3 normal=face.getUnitVec3(),along=face.getAxis()==Direction.Axis.X?new Vec3(0,0,8):new Vec3(8,0,0);
            var surfacePatch=new net.krodark.asterion.update.underworld.WebPatch(face.ordinal(),List.of(Vec3.ZERO,along),
                    List.of(normal,normal),List.of(new net.krodark.asterion.update.underworld.WebPatch.Edge(0,1)));
            var surfaceTrip=new net.krodark.asterion.update.underworld.entity.SpiderWebTrip(surfacePatch);
            Vec3 first=surfaceTrip.goal(body);
            double clearance=first.add(0,h/2,0).dot(normal);
            require(Math.abs(clearance-(face.getAxis()==Direction.Axis.Y?h:w)/2-.08)<1e-6,"Anchor approach clips enlarged body on "+face);
            require(!surfaceTrip.visit(first,body,true) && surfaceTrip.visit(surfaceTrip.goal(body),body,true),"Anchor trip failed on "+face);
        }
        for (Direction wall : Direction.Plane.HORIZONTAL) {
            Vec3 heading = new Vec3(0,1,0);
            for (int tick=0;tick<160;tick++) {
                Vec3 desired=new Vec3(5,-tick*.3,4);
                heading = SpiderSurfaceMotion.heading(wall,desired,heading,false);
                require(heading.y <= 0, "Wall steering climbed away from ground prey");
                require(heading.dot(SpiderSurfaceMotion.tangent(wall,desired).normalize())>.999,
                        "Wall steering ignored projected goal");
                require(Math.abs(heading.dot(wall.getUnitVec3())) < 1e-8,"Heading left wall plane");
                checks++;
            }
            Vec3 ceiling = SpiderSurfaceMotion.cornerHeading(wall,Direction.UP,new Vec3(0,1,0));
            require(ceiling.dot(wall.getUnitVec3()) < -.99,"Ceiling turn did not leave old wall");
            require(Math.abs(ceiling.y) < 1e-8,"Ceiling turn retained upward velocity");
            for (int tick=0;tick<100;tick++) {
                ceiling = SpiderSurfaceMotion.heading(Direction.UP,new Vec3(0,-20,0),ceiling,false);
                require(ceiling.dot(wall.getUnitVec3()) < -.99,"Normal-only goal changed ceiling heading");
                checks++;
            }
        }
        var corner = CentipedeSurfaceProbe.ahead(new AABB(0,0,0,1.45,1.3,1.45),
                new Vec3(0,.3,0),Direction.EAST,List.of(new AABB(-5,1.35,-5,5,2.35,5)));
        require(corner != null && corner.face()==Direction.UP,"Wall-to-ceiling support probe missed");
        for (Direction face : Direction.values()) {
            Vec3 forward = SpiderSurfaceMotion.heading(face,new Vec3(.3,.8,.6),Vec3.ZERO,false);
            Vec3 turned=forward;
            for(int i=0;i<12;i++) {
                Vec3 next=SpiderSurfaceMotion.turn(face.getUnitVec3(),turned,forward.scale(-1),.42);
                require(next.dot(turned)>=Math.cos(.42001),"Turn exceeded angular speed limit");
                require(Math.abs(next.dot(face.getUnitVec3()))<1e-8,"Turn left support plane");
                require(Double.isFinite(next.x) && Math.abs(next.length()-1)<1e-8,"Reversal collapsed heading");
                turned=next;checks++;
            }
            require(turned.dot(forward)<-.999,"Bounded turn never completed reversal");
            Quaternionf expected = null;
            for (int yaw=-180;yaw<=180;yaw+=5) {
                Quaternionf base = new Quaternionf().rotationY((float)Math.toRadians(180-yaw));
                Quaternionf world = new Quaternionf(base).mul(SurfaceOrientation.beetleSurfaceRotation(face.getUnitVec3(),forward,yaw));
                if (expected == null) expected = new Quaternionf(world);
                require(Math.abs(expected.dot(world))>.9999,"Body yaw changed world orientation on "+face);
                Vector3f up = world.transform(new Vector3f(0,1,0));
                require(world.transform(new Vector3f(0,0,-1)).distance(new Vector3f((float)forward.x,(float)forward.y,(float)forward.z))<.001,
                        "Rendered spider faces backwards");
                require(up.distance(new Vector3f((float)-face.getStepX(),(float)-face.getStepY(),(float)-face.getStepZ()))<.001,
                        "Body not aligned to support");
                checks++;
            }
        }
        System.out.println("Spider movement: "+checks+" steering/orientation checks plus wall-to-ceiling probe passed.");
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}

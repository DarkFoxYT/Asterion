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
            Quaternionf expected = null;
            for (int yaw=-180;yaw<=180;yaw+=5) {
                Quaternionf base = new Quaternionf().rotationY((float)Math.toRadians(180-yaw));
                Quaternionf world = new Quaternionf(base).mul(SurfaceOrientation.beetleSurfaceRotation(face.getUnitVec3(),forward,yaw));
                if (expected == null) expected = new Quaternionf(world);
                require(Math.abs(expected.dot(world))>.9999,"Body yaw changed world orientation on "+face);
                Vector3f up = world.transform(new Vector3f(0,1,0));
                require(up.distance(new Vector3f((float)-face.getStepX(),(float)-face.getStepY(),(float)-face.getStepZ()))<.001,
                        "Body not aligned to support");
                checks++;
            }
        }
        System.out.println("Spider movement: "+checks+" steering/orientation checks plus wall-to-ceiling probe passed.");
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}

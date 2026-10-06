import net.krodark.asterion.entity.*;
import net.krodark.asterion.update.underworld.entity.SpiderSurfaceMotion;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CreatureMotionSmoke {
    public static void main(String[] args) {
        var wall = new AABB(1, 0, -3, 2, 5, 3);
        var ascent = new Vec3(0, .2, .1);
        var lowHead = new AABB(.2, 2, -.3, .9, 2.5, .3);
        var highHead = lowHead.move(0, 2.8, 0);
        if (!CentipedeSurfaceProbe.limitWallAscent(lowHead, ascent, net.minecraft.core.Direction.EAST, List.of(wall)).equals(ascent))
            throw new AssertionError("Wall guard disables supported climbing");
        var limited = CentipedeSurfaceProbe.limitWallAscent(highHead, ascent, net.minecraft.core.Direction.EAST, List.of(wall));
        if (limited.y != 0 || limited.z != ascent.z)
            throw new AssertionError("Centipede can climb over a wall crest or loses sideways movement");
        if (!CentipedeSurfaceProbe.limitWallAscent(highHead, new Vec3(0,-.2,0), net.minecraft.core.Direction.EAST, List.of(wall)).equals(new Vec3(0,-.2,0)))
            throw new AssertionError("Wall guard prevents returning down the wall");
        var calls = new AtomicInteger();
        var floor = new AABB(-128,-1,-128,128,0,128);
        var collision = new CentipedeCollision(area -> { calls.incrementAndGet(); return List.of(floor); });
        var query = new AABB(-3,-2,-3,3,3,3);
        collision.beginFrame();
        var first = collision.collect(query);
        int once = calls.get();
        for (int i=0;i<32;i++) if (!collision.collect(query).equals(first)) throw new AssertionError("Cached collision changes geometry");
        if(calls.get()!=once || first.size()!=1) throw new AssertionError("Overlapping segments rescan or duplicate blocks");
        collision.beginFrame(); collision.collect(query);
        if(calls.get()!=once*2) throw new AssertionError("Frame cache conceals terrain changes");

        var chain = new CentipedeChain();
        var head = new Vec3(0,CentipedeFrame.CLEARANCE,0);
        Vec3 previous = null;
        double jitter = 0;
        for(int tick=0;tick<160;tick++) {
            collision.beginFrame();
            chain.tick(head,CentipedeFrame.DOWN,new Vec3(1,0,0),32,collision);
            for(int link=0;link<32;link++) {
                var pose=chain.sample(link,1);
                if(!Double.isFinite(pose.position().lengthSqr()) || pose.position().y < CentipedeFrame.HALF_HEIGHT)
                    throw new AssertionError("Segment loses floor contact");
            }
            Vec3 tail=chain.sampleSmoothed(31,.5F).position();
            if(tick>100 && previous!=null)jitter=Math.max(jitter,tail.distanceTo(previous));
            previous=tail;
        }
        if(jitter>1e-6) throw new AssertionError("Settled tail jitters: "+jitter);
        Vec3 turned=SpiderSurfaceMotion.transport(CentipedeFrame.DOWN,new Vec3(1,0,0),new Vec3(1,0,0));
        if(Math.abs(turned.x)>1e-6 || turned.y<.999) throw new AssertionError("Floor-to-wall heading reverses or stalls");
        System.out.println("PASS 32-segment cache reuse, terrain refresh, stable floor contacts and spider corner transport; idle jitter="+jitter);
    }
}

import net.krodark.asterion.physics.SegmentedChain;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class ChainPhysicsSmoke {
    public static void main(String[] args) {
        checkFlatLinks();
        checkLatchOrbit();
        checkHangingLeafSpans();
        checkAnchoredSpans();
        int cases = 0;
        for (int length : new int[]{1, 2, 8, 16, 24,64}) {
            Vec3 anchor = new Vec3(0, 30, 0);
            SegmentedChain chain = new SegmentedChain(anchor, anchor.add(0, -length, 0), length * 4, length);
            for (int tick=0;tick<300;tick++) {
                chain.step(anchor, anchor.add(0, -length, 0), false, (from,to)->to,
                        tick < 50 ? List.of(anchor.add(.2, -length * .5, 0)) : List.of());
                Vec3[] points = chain.rendered(1);
                if (points[0].distanceToSqr(anchor) > 1e-10) throw new AssertionError("Anchor drift");
                for (Vec3 point : points) if (!Double.isFinite(point.lengthSqr()) || point.distanceTo(anchor)>length+2)
                    throw new AssertionError("Unstable hanging chain at length " + length);
            }
            Vec3[] points = chain.rendered(1);
            double stretch = 0;
            for (int i=1;i<points.length;i++) stretch += points[i].distanceTo(points[i-1]);
            if (stretch > length * 1.12) throw new AssertionError("Chain stretch " + stretch + " for " + length);
            cases++;
        }
        Vec3 extendingAnchor=new Vec3(0,10,0);
        SegmentedChain shortChain=new SegmentedChain(extendingAnchor,extendingAnchor.add(0,-1,0),4,1);
        for(int tick=0;tick<10;tick++)shortChain.step(extendingAnchor,extendingAnchor.add(0,-1,0),false,(from,to)->to,List.of(extendingAnchor.add(.15,-.5,0)));
        Vec3[] original=shortChain.rendered(1),extended=shortChain.extended(8,2).rendered(1);
        for(int i=0;i<original.length;i++)if(original[i].distanceTo(extended[i])>1e-10)throw new AssertionError("Extending a chain snaps existing links");
        if(extended[extended.length-1].distanceTo(original[original.length-1].add(0,-1,0))>1e-10)throw new AssertionError("Placement adds more than one block");
        Vec3 start = new Vec3(0, 12, 0);
        SegmentedChain resting = new SegmentedChain(start,start.add(0,-8,0),32,8);
        double jitter=0;
        for(int tick=0;tick<600;tick++) {
            Vec3[] before=resting.rendered(1);
            resting.step(start,start.add(0,-8,0),false,(from,to)->to,List.of(start.add(.2,-4,0)));
            if(tick>500) {
                Vec3[] after=resting.rendered(1);
                for(int i=0;i<before.length;i++)jitter=Math.max(jitter,before[i].distanceTo(after[i]));
            }
        }
        if(jitter>.01)throw new AssertionError("Stationary contact jitters " + jitter);
        var grip=new net.krodark.asterion.physics.ChainGripSelection<Object,Object,Object>();
        Object client=new Object(),server=new Object(),clientPlayer=new Object(),serverPlayer=new Object();
        Object chainA=new Object(),chainB=new Object();
        var id=java.util.UUID.randomUUID();
        if(grip.target(client,id,clientPlayer)!=null)throw new AssertionError("Climbing activates without clicking");
        grip.toggle(client,id,clientPlayer,chainA);grip.toggle(server,id,serverPlayer,chainB);
        if(grip.target(client,id,clientPlayer)!=chainA || grip.target(server,id,serverPlayer)!=chainB)
            throw new AssertionError("Integrated-server selections interfere");
        grip.toggle(client,id,clientPlayer,chainB);
        if(grip.target(client,id,clientPlayer)!=chainB)throw new AssertionError("Selection did not change");
        grip.toggle(client,id,clientPlayer,chainB);
        if(grip.target(client,id,clientPlayer)!=null)throw new AssertionError("Second click did not release");
        grip.select(client,id,clientPlayer,chainA);grip.select(client,id,clientPlayer,chainA);
        if(grip.target(client,id,clientPlayer)!=chainA)throw new AssertionError("Repeated use releases a held chain");
        grip.release(client,id);
        if(grip.target(client,id,clientPlayer)!=null)throw new AssertionError("Releasing right-click keeps grip");
        if(grip.target(server,id,new Object())!=null)throw new AssertionError("Respawn inherits grip");
        SegmentedChain tether = new SegmentedChain(start, start.add(1, 0, 0), 64, 2);
        for (int tick=0;tick<240;tick++) {
            double reach = 2 + 24 * Math.sin(Math.PI * Math.min(tick, 120) / 120);
            Vec3 end = start.add(reach, Math.sin(tick * .1), 0);
            tether.payout(start.distanceTo(end)+.6);
            tether.step(start, end, true, (from,to)->new Vec3(to.x, Math.max(0,to.y),to.z), List.of());
            Vec3[] points=tether.rendered(1);
            if (points[0].distanceToSqr(start)>1e-10 || points[points.length-1].distanceToSqr(end)>1e-10)
                throw new AssertionError("Tether endpoint moved");
            for(Vec3 point:points)if(!Double.isFinite(point.lengthSqr()) || point.y<0 || point.distanceTo(start)>40)
                throw new AssertionError("Unstable payout/recall");
        }
        // The gameplay hooks must name real methods in this Minecraft version.
        try {
            net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("handleOnClimbable", Vec3.class);
            net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("onClimbable");
        } catch (ReflectiveOperationException ex) { throw new AssertionError("Climbing mixin target changed",ex); }
        System.out.println("PASS " + cases + " hanging chains, stationary-contact jitter=" + jitter
                + ", explicit grab/release, client/server isolation, respawn, payout, recall, and climbing targets");
    }
    private static void checkAnchoredSpans() {
        Vec3 start=new Vec3(0,30,0);
        for(Vec3 delta:new Vec3[]{new Vec3(12,0,0),new Vec3(8,4,8),new Vec3(0,-8,0)}) {
            Vec3 end=start.add(delta);double length=Math.ceil(delta.length()+3);
            var chain=new SegmentedChain(start,end,64,length);chain.sag(start,end);
            for(int tick=0;tick<400;tick++) {
                chain.step(start,end,true,(from,to)->to,List.of());
                Vec3[] points=chain.rendered(1);
                if(points[0].distanceToSqr(start)>1e-10 || points[points.length-1].distanceToSqr(end)>1e-10)throw new AssertionError("Span anchors drift");
                for(Vec3 point:points)if(!Double.isFinite(point.lengthSqr()) || point.distanceTo(start)>length+1)throw new AssertionError("Unstable omni-directional span");
            }
            var body=start.lerp(end,.5);
            var axis=chain.tangentAt(body);if(Math.abs(axis.length()-1)>1e-6)throw new AssertionError("Invalid climbing tangent");
            var motion=net.krodark.asterion.physics.ChainClimbingMotion.velocity(body.add(0,-.9,-.48),body,axis,90,1,false);
            if(!Double.isFinite(motion.lengthSqr()) || motion.length()>.381)throw new AssertionError("Span grip snaps");
        }
        System.out.println("PASS fixed horizontal, diagonal and vertical span anchors, sag and tangent climbing");
    }
    private static void checkLatchOrbit() {
        var center=new Vec3(0,5,0);
        Vec3 feet=new Vec3(2,4.1,-1);
        for(int tick=0;tick<120;tick++) {
            var motion=net.krodark.asterion.physics.ChainClimbingMotion.velocity(feet,center,0,0,false);
            if(motion.length()>.221 || motion.y!=0)throw new AssertionError("Latch snaps or slips");
            feet=feet.add(motion);
        }
        if(feet.distanceTo(new Vec3(0,4.1,-.48))>.001)throw new AssertionError("Latch fails to settle");
        for(int tick=0;tick<180;tick++) {
            var motion=net.krodark.asterion.physics.ChainClimbingMotion.velocity(feet,center,180,0,false);
            if(motion.length()>.16)throw new AssertionError("Camera turning snaps body around chain");
            feet=feet.add(motion);
            if(Math.hypot(feet.x,feet.z)<.4)throw new AssertionError("Orbit crosses through chain");
        }
        if(feet.distanceTo(new Vec3(0,4.1,.48))>.001)throw new AssertionError("Orbit doesn't follow view");
        var climb=net.krodark.asterion.physics.ChainClimbingMotion.velocity(feet,center,180,1,false);
        var descend=net.krodark.asterion.physics.ChainClimbingMotion.velocity(feet,center,180,-1,false);
        var hold=net.krodark.asterion.physics.ChainClimbingMotion.velocity(feet,center,180,1,true);
        if(climb.y!=.16 || descend.y!=-.16 || hold.y!=0)throw new AssertionError("Climb controls broken");
        System.out.println("PASS smooth latch, no idle slip, 180-degree orbit without crossing chain, climb/descend/crouch");
    }
    private static void checkHangingLeafSpans() {
        for(int thickness=3;thickness<=5;thickness++)for(int diagonal:new int[]{0,12}) {
            var start=new net.minecraft.core.BlockPos(0,24,0);
            var end=new net.minecraft.core.BlockPos(20,24,diagonal);
            var blocks=net.krodark.asterion.worldgen.HangingLeafSpan.blocks(start,end,thickness,5);
            if(!blocks.contains(start)||!blocks.contains(end)||blocks.size()>3000)throw new AssertionError("Unbounded/unanchored leaf span");
            var mid=net.krodark.asterion.worldgen.HangingLeafSpan.point(Vec3.atCenterOf(start),Vec3.atCenterOf(end),.5,5);
            if(mid.y!=19.5)throw new AssertionError("Missing slack");
            var remaining=new java.util.HashSet<>(blocks);var queue=new java.util.ArrayDeque<net.minecraft.core.BlockPos>();
            queue.add(start);remaining.remove(start);
            while(!queue.isEmpty()) {
                var pos=queue.remove();
                for(var direction:net.minecraft.core.Direction.values())if(remaining.remove(pos.relative(direction)))queue.add(pos.relative(direction));
            }
            if(!remaining.isEmpty())throw new AssertionError("Diagonal leaf vine has disconnected blocks");
        }
        System.out.println("PASS 3–5-block leaf tubes: anchored, sagging, connected diagonal spans and bounded block count");
    }
    private static java.util.List<Vec3> geometry(double length, Vec3 camera) {
        return geometry(length,camera,false);
    }
    private static java.util.List<Vec3> geometry(double length, Vec3 camera,boolean hanging) {
        var vertices = new java.util.ArrayList<Vec3>();
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                ChainPhysicsSmoke.class.getClassLoader(), new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addVertex")) {
                        int i=args.length==4?1:0;
                        vertices.add(new Vec3(((Number)args[i]).doubleValue(),((Number)args[i+1]).doubleValue(),((Number)args[i+2]).doubleValue()));
                    }
                    if (!hanging && method.getName().equals("setUv")) for(Object arg:args)
                        if (((Number)arg).doubleValue()!=0 && ((Number)arg).doubleValue()!=.25)
                            throw new AssertionError("Chain does not sample the full pixel sprite");
                    return method.getReturnType()==void.class?null:proxy;
                });
        if(hanging)net.krodark.asterion.client.render.entity.ChainGeometry.drawHanging(new com.mojang.blaze3d.vertex.PoseStack().last(),
                consumer,new Vec3[]{Vec3.ZERO,new Vec3(0,-length,0)},Vec3.ZERO,camera,0);
        else net.krodark.asterion.client.render.entity.ChainGeometry.draw(new com.mojang.blaze3d.vertex.PoseStack().last(),
                consumer,new Vec3[]{Vec3.ZERO,new Vec3(0,-length,0)},Vec3.ZERO,camera,0);
        return vertices;
    }
    private static void checkFlatLinks() {
        var before=geometry(10,new Vec3(0,0,5));
        var after=geometry(10.01,new Vec3(0,0,5));
        if(before.size()!=40*8)throw new AssertionError("Unexpected link count");
        for(int i=0;i<before.size();i++) {
            if(before.get(i).z!=0)throw new AssertionError("Extruded chain geometry");
            if(before.get(i).distanceToSqr(after.get(i))>1e-10)throw new AssertionError("Length changes shuffle links");
        }
        for(Vec3 v:geometry(10,new Vec3(0,5,0)))
            if(!Double.isFinite(v.lengthSqr()))throw new AssertionError("Collinear camera breaks chain sprites");
        var hanging=geometry(10,new Vec3(0,0,5),true);
        if(hanging.size()!=10*16)throw new AssertionError("Hanging chains don't have two double-sided planes");
        var otherView=geometry(10,new Vec3(5,0,0),true);
        for(int i=0;i<hanging.size();i++)if(hanging.get(i).distanceToSqr(otherView.get(i))>1e-10)throw new AssertionError("Crossed chains rotate with camera");
        for(Vec3 v:geometry(10.01,new Vec3(0,0,5),true))
            if(v.y>1e-6 || v.y< -10.01001 || Math.abs(Math.abs(v.x)-Math.abs(v.z))>1e-6)throw new AssertionError("Last tile overshoots or crossed planes mismatch");
        System.out.println("PASS flat double-sided sprites, full texture UVs, anchored spacing, and collinear camera");
    }
}

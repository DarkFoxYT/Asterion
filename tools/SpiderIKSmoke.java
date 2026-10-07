package net.krodark.asterion.update.underworld.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Exercise real model dimensions, mirrored chains, reachable and unreachable targets. */
public final class SpiderIKSmoke {
    public static void main(String[] args) throws Exception {
        var document = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/asterion/geckolib/models/entity/spider.geo.json"))).getAsJsonObject();
        Map<String,JsonObject> bones = new HashMap<>();
        for (var value : document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            var bone = value.getAsJsonObject(); bones.put(bone.get("name").getAsString(),bone);
        }
        int cases = 0;
        var farOrigin=new net.minecraft.world.phys.Vec3(12000000.125,70,-12000000.375);
        var localFoot=new Vector3f(.12345F,.02F,-.54321F);
        var farFoot=SpiderLegIK.toWorld(new Matrix4f(),localFoot,farOrigin).subtract(farOrigin);
        if(farFoot.distanceTo(new net.minecraft.world.phys.Vec3(localFoot.x,localFoot.y,localFoot.z))>1e-7)
            throw new AssertionError("Foot placement loses precision far from spawn");
        if(!bones.containsKey("webmaker") || !bones.get("webmaker").get("parent").getAsString().equals("abnomen"))
            throw new AssertionError("Missing authored spinneret attachment");
        for(int leg=0;leg<8;leg++) {
            var last=SpiderLegIK.airborneOffset(leg,0,42);
            double motion=0;
            for(int tick=1;tick<=200;tick++) {
                var next=SpiderLegIK.airborneOffset(leg,tick*.1F,42);
                if(next.length()>.55 || next.distanceTo(last)>.03)
                    throw new AssertionError("Airborne leg motion is unbounded or discontinuous");
                motion+=next.distanceTo(last);last=next;
            }
            if(motion<.5)throw new AssertionError("Airborne leg stopped animating");
        }
        for (String name : bones.keySet()) {
            if (!name.contains("leg") || name.endsWith("mid") || name.endsWith("end")) continue;
            Vector3f[] pivots = new Vector3f[3], rest = new Vector3f[3];
            String[] names = {name,name+"mid",name+"end"};
            var endCube=bones.get(names[2]).getAsJsonArray("cubes").get(0).getAsJsonObject();
            if(Math.abs(endCube.getAsJsonArray("size").get(0).getAsFloat()-21.333333F)>.0001F)
                throw new AssertionError("Distal plane was not shortened: "+name);
            var midCubes=bones.get(names[1]).getAsJsonArray("cubes");
            float authoredWidth=name.contains("ish")?25:24;
            if(midCubes.get(1).getAsJsonObject().getAsJsonArray("size").get(0).getAsFloat()!=authoredWidth)
                throw new AssertionError("Middle segment was changed: "+name);
            for (int j=0;j<3;j++) {
                pivots[j] = vector(bones.get(names[j]),"pivot").mul(-1,1,1).div(16);
                rest[j] = vector(bones.get(names[j]),"rotation").mul(-1,-1,1).mul((float)Math.PI/180);
            }
            Vector3f tip = new Vector3f(pivots[2]).add(name.startsWith("left") ? -13F/16 : 13F/16,-.375F,0);
            Vector3f nominal = endpoint(pivots,rest,tip);
            int ordinal=name.contains("frontish")?1:name.contains("backish")?2:name.contains("front")?0:3;
            int legIndex=ordinal+(name.startsWith("left")?0:4);
            if(!SpiderLegIK.ownsFoot(legIndex,nominal,pivots[0]))throw new AssertionError("Authored resting foot lost its lane: "+name+" "+nominal);
            Vector3f hangingTarget=new Vector3f(nominal).lerp(pivots[0],.12F).add(0,.12F,0);
            Vector3f[] hanging={new Vector3f(rest[0]),new Vector3f(rest[1]),new Vector3f(rest[2])};
            SpiderLegIK.solve(pivots,rest,hanging,tip,hangingTarget);
            if(endpoint(pivots,hanging,tip).distance(hangingTarget)>.075F)
                throw new AssertionError(name+" cannot grasp its suspended silk cradle");
            // Follow an entire grounded swing with the real chain. A target
            // lifting is insufficient if the solved, visible foot stays down.
            var from=new net.minecraft.world.phys.Vec3(nominal.x,.30,nominal.z);
            var to=from.add(0,0,.4);
            Vector3f[] walking={new Vector3f(rest[0]),new Vector3f(rest[1]),new Vector3f(rest[2])};
            for(int frame=0;frame<=24;frame++) {
                var target=SpiderLegIK.swingFoot(from,to,new net.minecraft.world.phys.Vec3(0,1,0),frame/24.0,1.5,new net.minecraft.world.phys.Vec3(name.startsWith("left")?-1:1,0,0));
                SpiderLegIK.solve(pivots,rest,walking,tip,new Vector3f((float)target.x,(float)target.y,(float)target.z));
                var actual=endpoint(pivots,walking,tip);
                if(actual.distance(new Vector3f((float)target.x,(float)target.y,(float)target.z))>.075)
                    throw new AssertionError(name+" visible foot cannot follow swing");
                if(frame==12 && actual.y<.55)throw new AssertionError(name+" visible foot does not lift");
            }
            for (Vector3f shift : new Vector3f[]{new Vector3f(),new Vector3f(0,.25F,0),
                    new Vector3f(.15F,.4F,-.12F),new Vector3f(0,-.2F,.15F),
                    new Vector3f(0,.08F-nominal.y,0),new Vector3f(0,.30F-nominal.y,0),new Vector3f(100,100,100)}) {
                Vector3f[] angles = {new Vector3f(rest[0]),new Vector3f(rest[1]),new Vector3f(rest[2])};
                Vector3f target = new Vector3f(nominal).add(shift);
                SpiderLegIK.solve(pivots,rest,angles,tip,target);
                float error = endpoint(pivots,angles,tip).distance(target);
                if (shift.length()<10 && error>.075F) throw new AssertionError(name+" residual "+error+" shift "+shift);
                for (int j=0;j<3;j++) for (int axis=0;axis<3;axis++) {
                    float delta = angles[j].get(axis)-rest[j].get(axis);
                    if (!Float.isFinite(delta) || Math.abs(delta)>.851F) throw new AssertionError("Joint limit");
                    if (shift.lengthSquared()==0 && Math.abs(delta)>.00001F) throw new AssertionError("Rest pose changed");
                }
                // All six attachment frames must preserve the local target through a world round trip.
                for (Vector3f normal : new Vector3f[]{new Vector3f(0,1,0),new Vector3f(0,-1,0),
                        new Vector3f(1,0,0),new Vector3f(-1,0,0),new Vector3f(0,0,1),new Vector3f(0,0,-1)}) {
                    Matrix4f world = new Matrix4f().translation(5,12,-8).rotateY(.8F)
                            .rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0,1,0),normal))
                            .scale(net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE);
                    Vector3f back = new Matrix4f(world).invert().transformPosition(world.transformPosition(new Vector3f(target)));
                    if (back.distance(target)>.0001F) throw new AssertionError("Surface transform");
                    cases++;
                }
            }
        }
        for(int leg=0;leg<8;leg++) {
            float side=leg<4?-1:1;
            float z=switch(leg%4) { case 0->-1;case 1->-.25F;case 2->.25F;default->1; };
            Vector3f hip=new Vector3f(side*.3F,.8F,z*.25F);
            if(!SpiderLegIK.ownsFoot(leg,new Vector3f(side*1.8F,0,z),hip))throw new AssertionError("Leg lost its own foot sector");
            if(SpiderLegIK.ownsFoot(leg,new Vector3f(-side*1.8F,0,z),hip))throw new AssertionError("Leg crosses the body");
            if(SpiderLegIK.ownsFoot(leg,new Vector3f(side*1.8F,0,leg%4<2?2:-2),hip))throw new AssertionError("Leg leaves its reachable fan");
        }
        checkWalkingGait();
        checkNeighbourClearance(bones);
        checkStaggeredFootfalls();
        for(var face:net.minecraft.core.Direction.values()) {
            var normal=face.getUnitVec3();
            var contact=new net.minecraft.world.phys.Vec3(2,3,4);
            var hovering=contact.add(normal.scale(.3)).add(.1,.2,.15);
            var settled=SpiderLegIK.onContactPlane(hovering,contact,normal);
            if(Math.abs(settled.subtract(contact).dot(normal))>1e-8)
                throw new AssertionError("Blended touchdown floats above the contact plane on "+face);
        }
        // A foot crossing from floor to wall lifts outside both contact planes.
        var floorUp=new net.minecraft.world.phys.Vec3(0,1,0);
        var wallUp=new net.minecraft.world.phys.Vec3(1,0,0);
        var cornerFrom=new net.minecraft.world.phys.Vec3(.4,0,0);
        var cornerTo=new net.minecraft.world.phys.Vec3(0,.4,0);
        for(int frame=0;frame<=100;frame++) {
            double t=frame/100.0;
            var up=floorUp.lerp(wallUp,t*t*(3-2*t)).normalize();
            var foot=SpiderLegIK.swingFoot(cornerFrom,cornerTo,up,t,1.5);
            if(foot.x<-.000001 || foot.y<-.000001 || !Double.isFinite(foot.length()))
                throw new AssertionError("Corner swing enters floor/wall or loses its normal");
        }
        for(net.minecraft.core.Direction face:net.minecraft.core.Direction.values()) {
            var up=face.getUnitVec3();
            var across=face.getAxis()==net.minecraft.core.Direction.Axis.X
                    ?new net.minecraft.world.phys.Vec3(0,0,1):new net.minecraft.world.phys.Vec3(1,0,0);
            var from=net.minecraft.world.phys.Vec3.ZERO;
            var to=across.add(up);
            if(SpiderLegIK.swingFoot(from,to,up,0).distanceTo(from)>1e-8
                    || SpiderLegIK.swingFoot(from,to,up,1).distanceTo(to)>1e-8)
                throw new AssertionError("Swing endpoints moved");
            for(int i=30;i<=70;i++) {
                var foot=SpiderLegIK.swingFoot(from,to,up,i/100.0);
                if(foot.dot(up)<1.17)throw new AssertionError("Foot crossed riser below tread");
            }
            var q=new org.joml.Quaternionf().rotationTo(new Vector3f(0,1,0),
                    new Vector3f(face.getStepX(),face.getStepY(),face.getStepZ()));
            for(float slope:new float[]{0,.15F,4}) {
                var feet=new java.util.ArrayList<SpiderLegIK.Leg>();
                for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                    Vector3f p=q.transform(new Vector3f(x,slope*x,z));
                    var foot=new net.minecraft.world.phys.Vec3(p.x+10,p.y+20,p.z-30);
                    feet.add(new SpiderLegIK.Leg(java.util.List.of(foot),foot,true,0));
                }
                var tilt=SpiderLegIK.bodyTilt(new SpiderLegIK.Debug(feet,0),q);
                if(tilt.length()>Math.toRadians(16)+1e-6)throw new AssertionError("Excessive body lean");
                if(slope==0 && tilt.length()>1e-6)throw new AssertionError("Flat feet tilt body");
                if(slope==0) {
                    Vector3f loaded=q.transform(new Vector3f(.3F,0,-.25F));
                    var shift=new net.minecraft.world.phys.Vec3(loaded.x,loaded.y,loaded.z);
                    var weight=SpiderLegIK.bodyTilt(new SpiderLegIK.Debug(feet,0,shift),q);
                    if(weight.x<.03 || weight.z<.04)throw new AssertionError("Level-ground weight transfer did not move torso");
                    double settled=.12;
                    for(int tick=0;tick<200;tick++)settled=SpiderLegIK.bodyLift(new SpiderLegIK.Debug(feet,0,shift),q,settled,net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE);
                    if(Math.abs(settled-.12)>1e-6)throw new AssertionError("Horizontal stance error keeps moving body height");
                }
                if(slope>0 && tilt.z<=0)throw new AssertionError("Lean does not follow feet");
                var tripod=SpiderLegIK.bodyTilt(new SpiderLegIK.Debug(feet.subList(0,3),0),q);
                if(tripod.distanceTo(tilt)>.0001)throw new AssertionError("Three-foot support does not drive torso");
                Vector3f liftAxis=q.transform(new Vector3f(0,1,0));
                var error=new net.minecraft.world.phys.Vec3(liftAxis.x*.2,liftAxis.y*.2,liftAxis.z*.2);
                double lift=SpiderLegIK.bodyLift(new SpiderLegIK.Debug(feet,0,error),q,0,net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE);
                if(Math.abs(lift-.025)>.001)
                    throw new AssertionError("Torso did not rise toward planted feet on "+face);
                if(SpiderLegIK.bodyLift(new SpiderLegIK.Debug(feet,0,error.scale(-1)),q,0,net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE)>=0)
                    throw new AssertionError("Torso did not lower toward feet");
                if(Math.abs(SpiderLegIK.bodyLift(new SpiderLegIK.Debug(feet,0,error.scale(100)),q,.25,net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE)-.26)>.0001)
                    throw new AssertionError("Torso height escaped clamp");
                if(SpiderLegIK.bodyTilt(new SpiderLegIK.Debug(feet.subList(0,2),0),q).lengthSqr()!=0)
                    throw new AssertionError("Unstable two-foot body lean");
            }
        }
        System.out.println("Spider IK: "+cases+" cases, visible foot swings and body tilt on all six surfaces passed.");
    }
    private static void checkNeighbourClearance(Map<String,JsonObject> bones) {
        String[] names={"leftlegfront","leftlegfrontish","leftlegbackish","leftlegback","rightlegfront","rightlegfrontish","rightlegbackish","rightlegback"};
        Vector3f[][] rest=new Vector3f[8][3],angles=new Vector3f[8][3],points=new Vector3f[8][3];
        Vector3f[] tips=new Vector3f[8],nominal=new Vector3f[8];
        for(int leg=0;leg<8;leg++) {
            for(int j=0;j<3;j++) {
                String name=names[leg]+(j==0?"":j==1?"mid":"end");
                points[leg][j]=vector(bones.get(name),"pivot").mul(-1,1,1).div(16);
                rest[leg][j]=vector(bones.get(name),"rotation").mul(-1,-1,1).mul((float)Math.PI/180);
                angles[leg][j]=new Vector3f(rest[leg][j]);
            }
            tips[leg]=new Vector3f(points[leg][2]).add(leg<4?-13F/16:13F/16,-.375F,0);
            nominal[leg]=endpoint(points[leg],rest[leg],tips[leg]);
        }
        for(int frame=0;frame<240;frame++) {
            var solved=new java.util.ArrayList<Vector3f[]>();
            for(int leg=0;leg<8;leg++) {
                double phase=frame*.09+((leg+leg/4)&1)*Math.PI;
                Vector3f target=new Vector3f(nominal[leg]).add(0,(float)(.08+.28*Math.max(0,Math.sin(phase))),(float)(.22*Math.cos(phase)));
                if(leg%4==1)target.z=Math.clamp(target.z,-.40F,-.10F);
                if(leg%4==2)target.z=Math.clamp(target.z,.10F,.40F);
                var neighbours=new java.util.ArrayList<Vector3f[]>();
                for(int other=leg/4*4;other<leg;other++)neighbours.add(solved.get(other));
                Vector3f[] before={new Vector3f(angles[leg][0]),new Vector3f(angles[leg][1]),new Vector3f(angles[leg][2])};
                SpiderLegIK.solve(points[leg],rest[leg],angles[leg],tips[leg],target,neighbours);
                if(frame>0)for(int j=0;j<3;j++) {
                    Vector3f desired=new Vector3f(angles[leg][j]);
                    angles[leg][j].set(before[j]);SpiderLegIK.settleJoint(angles[leg][j],desired,1.0/3,j);
                }
                if(endpoint(points[leg],angles[leg],tips[leg]).distance(target)>.09F)
                    throw new AssertionError("Joint settling loses walking foot reach: "+names[leg]);
                if(frame>0)for(int j=0;j<3;j++)for(int axis=0;axis<3;axis++)
                    if(Math.abs(angles[leg][j].get(axis)-before[j].get(axis))>.20)
                        throw new AssertionError("Moving knee jumps between poses: "+names[leg]);
                Matrix4f transform=new Matrix4f();Vector3f[] joints=new Vector3f[4];
                for(int j=0;j<3;j++) {
                    joints[j]=transform.transformPosition(new Vector3f(points[leg][j]));
                    transform.translate(points[leg][j]).rotateZYX(angles[leg][j].z,angles[leg][j].y,angles[leg][j].x).translate(new Vector3f(points[leg][j]).negate());
                }
                joints[3]=transform.transformPosition(new Vector3f(tips[leg]));
                for(Vector3f[] other:neighbours)for(int a=1;a<3;a++)for(int b=1;b<3;b++)
                    if(SpiderLegIK.segmentDistance(joints[a],joints[a+1],other[b],other[b+1])<.05F)
                        throw new AssertionError("Walking limbs intersect: "+names[leg]+" frame="+frame);
                solved.add(joints);
            }
        }
    }
    private static void checkStaggeredFootfalls() {
        for(int fps:new int[]{30,60,144}) {
            var gait=new SpiderLegIK.Gait();
            var destinations=new net.minecraft.world.phys.Vec3[8];
            float[] first=new float[8];java.util.Arrays.fill(first,-1);
            for(int frame=0;frame<fps;frame++) {
                float age=frame*20F/fps;
                for(int leg=0;leg<8;leg++)if(first[leg]>=0 && age-first[leg]>3)destinations[leg]=null;
                gait.beginFrame(destinations,age);
                for(int leg=0;leg<8;leg++)if(first[leg]<0 && gait.canStart(leg)) {
                    first[leg]=age;destinations[leg]=net.minecraft.world.phys.Vec3.ZERO;
                }
            }
            for(int leg=0;leg<8;leg++)if(first[leg]<0)throw new AssertionError("Ripple gait starved leg "+leg);
            if(first[3]-first[1]<.34 || first[2]-first[0]<.34)
                throw new AssertionError("Front and rear footfalls are still synchronized at "+fps+"fps");
        }
        var from=net.minecraft.world.phys.Vec3.ZERO;
        var to=new net.minecraft.world.phys.Vec3(0,0,.4);
        var up=new net.minecraft.world.phys.Vec3(0,1,0);
        var side=new net.minecraft.world.phys.Vec3(1,0,0);
        var middle=SpiderLegIK.swingFoot(from,to,up,.5,1.5,side);
        if(middle.x<.15 || middle.y<.47)throw new AssertionError("Recovery stroke has no outward lift");
        if(SpiderLegIK.swingFoot(from,to,up,0,1.5,side).distanceTo(from)>1e-8
                || SpiderLegIK.swingFoot(from,to,up,1,1.5,side).distanceTo(to)>1e-8)
            throw new AssertionError("Recovery arc moves a planted endpoint");
    }

    private static void checkWalkingGait() {
        for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.03,.12,.3,.35})
                for (double scale : new double[]{1.25,1.5}) for (var face : net.minecraft.core.Direction.values()) {
            var gait = new SpiderLegIK.Gait();
            var feet = new net.minecraft.world.phys.Vec3[8];
            var starts = new net.minecraft.world.phys.Vec3[8];
            var destinations = new net.minecraft.world.phys.Vec3[8];
            var started = new double[8];
            var landed = new double[8];java.util.Arrays.fill(landed,-100);
            var steps = new int[8];
            var lifted = new boolean[8];
            var up = face.getUnitVec3();
            var forward = face.getAxis()==net.minecraft.core.Direction.Axis.X
                    ? new net.minecraft.world.phys.Vec3(0,0,1) : new net.minecraft.world.phys.Vec3(1,0,0);
            java.util.Arrays.fill(feet,net.minecraft.world.phys.Vec3.ZERO);

            // Start walking, then stop and allow the final adjustment to settle.
            for(int frame=0;frame<fps*12;frame++) {
                double age=frame*20.0/fps;
                var restFoot=forward.scale(Math.min(age,160)*speed);
                var velocity=age<160?forward.scale(speed):net.minecraft.world.phys.Vec3.ZERO;
                gait.beginFrame(destinations,(float)age);
                int airborne=0;
                for(int leg=0;leg<8;leg++) {
                    var target=restFoot.add(SpiderLegIK.strideLead(velocity,up,scale,leg));
                    var previous=feet[leg];
                    boolean wasStepping=destinations[leg]!=null;
                    if(!wasStepping && age-landed[leg]>=.65 && SpiderLegIK.needsWalkingStep(feet[leg],target,restFoot,velocity,up,scale)
                            && (SpiderLegIK.trailingStance(feet[leg],restFoot,velocity,up,scale)?gait.canStartEmergency(leg):gait.canStart(leg))) {
                        starts[leg]=feet[leg];destinations[leg]=target;started[leg]=age;steps[leg]++;
                    }
                    if(destinations[leg]!=null) {
                        double t=Math.clamp((age-started[leg])/SpiderLegIK.stepDuration(speed,scale,leg),0,1);
                        feet[leg]=SpiderLegIK.swingFoot(starts[leg],destinations[leg],up,t,scale);
                        if(feet[leg].dot(up)>.15*scale)lifted[leg]=true;
                        if(t>=1) { destinations[leg]=null;landed[leg]=age; }
                        else airborne++;
                    } else if(previous.distanceTo(feet[leg])>1e-10) {
                        throw new AssertionError("Planted foot slides");
                    }
                    if(age>20 && age<160 && restFoot.subtract(feet[leg]).dot(forward)>.85*scale+speed*20/fps)
                        throw new AssertionError("Walking foot trails body too far on "+face+" at "+fps+"fps speed="+speed+" leg="+leg+" age="+age+" lag="+restFoot.subtract(feet[leg]).dot(forward));
                    if(age>200 && destinations[leg]!=null)throw new AssertionError("Idle spider keeps shuffling");
                }
                if(airborne>4)throw new AssertionError("Lost support group");
            }
            for(int leg=0;leg<8;leg++) {
                if(steps[leg]<5 || !lifted[leg])throw new AssertionError("Leg starved or never visibly lifted: "+leg+" at "+fps+"fps");
                if(SpiderLegIK.needsStep(feet[leg],forward.scale(160*speed),up,scale))
                    throw new AssertionError("Foot did not catch up: fps="+fps+" speed="+speed+" scale="+scale+" leg="+leg+" offset="+feet[leg].subtract(forward.scale(160*speed))+" steps="+steps[leg]);
            }
        }
        System.out.println("Walking gait: all eight feet lift, alternate support and settle at 30/60/144 fps, three speeds, two sizes and six surfaces.");
    }

    private static Vector3f vector(JsonObject object,String key) {
        var a=object.getAsJsonArray(key);
        return a==null ? new Vector3f() : new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());
    }
    private static Vector3f endpoint(Vector3f[] pivots,Vector3f[] angles,Vector3f tip) {
        Matrix4f m=new Matrix4f();
        for(int j=0;j<3;j++) m.translate(pivots[j]).rotateZYX(angles[j].z,angles[j].y,angles[j].x)
                .translate(-pivots[j].x,-pivots[j].y,-pivots[j].z);
        return m.transformPosition(new Vector3f(tip));
    }
}

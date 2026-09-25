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
        for (String name : bones.keySet()) {
            if (!name.contains("leg") || name.endsWith("mid") || name.endsWith("end")) continue;
            Vector3f[] pivots = new Vector3f[3], rest = new Vector3f[3];
            String[] names = {name,name+"mid",name+"end"};
            for (int j=0;j<3;j++) {
                pivots[j] = vector(bones.get(names[j]),"pivot").mul(-1,1,1).div(16);
                rest[j] = vector(bones.get(names[j]),"rotation").mul(-1,-1,1).mul((float)Math.PI/180);
            }
            Vector3f tip = new Vector3f(pivots[2]).add(name.startsWith("left") ? -.9375F : .9375F,-.375F,0);
            Vector3f nominal = endpoint(pivots,rest,tip);
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
                            .rotate(new org.joml.Quaternionf().rotationTo(new Vector3f(0,1,0),normal)).scale(.7F);
                    Vector3f back = new Matrix4f(world).invert().transformPosition(world.transformPosition(new Vector3f(target)));
                    if (back.distance(target)>.0001F) throw new AssertionError("Surface transform");
                    cases++;
                }
            }
        }
        for(int movingLeg=0;movingLeg<8;movingLeg++) {
            net.minecraft.world.phys.Vec3[] stepping=new net.minecraft.world.phys.Vec3[8];
            stepping[movingLeg]=net.minecraft.world.phys.Vec3.ZERO;
            int allowed=0;
            for(int leg=0;leg<8;leg++)if(SpiderLegIK.canLift(leg,stepping))allowed++;
            if(allowed!=4)throw new AssertionError("Gait must keep opposite four legs planted");
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
                if(tilt.length()>Math.toRadians(4)+1e-6)throw new AssertionError("Excessive body lean");
                if(slope==0 && tilt.length()>1e-6)throw new AssertionError("Flat feet tilt body");
                if(slope>0 && tilt.z<=0)throw new AssertionError("Lean does not follow feet");
                if(SpiderLegIK.bodyTilt(new SpiderLegIK.Debug(feet.subList(0,2),0),q).lengthSqr()!=0)
                    throw new AssertionError("Unstable two-foot body lean");
            }
        }
        System.out.println("Spider IK: "+cases+" cases, eight support-group checks and body tilt on all six surfaces passed.");
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

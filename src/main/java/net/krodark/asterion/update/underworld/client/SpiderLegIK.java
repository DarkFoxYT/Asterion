package net.krodark.asterion.update.underworld.client;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Spider-only, rest-pose constrained three-joint CCD. No shared model bones are mutated. */
public final class SpiderLegIK {
    private static final String[] NAMES = {"leftlegfront", "leftlegfrontish", "leftlegbackish", "leftlegback",
            "rightlegfront", "rightlegfrontish", "rightlegbackish", "rightlegback"};
    private static final Map<LimboSpiderEntity, Debug> DEBUG = new WeakHashMap<>();
    public record Frame(LimboSpiderEntity spider, Vec3 origin, float age, Memory memory) { }
    public record Leg(List<Vec3> joints, Vec3 target, boolean contact, float error) { }
    public record Debug(List<Leg> legs, float age, Vec3 stanceError) {
        public Debug(List<Leg> legs,float age) { this(legs,age,Vec3.ZERO); }
    }
    public static Debug debug(LimboSpiderEntity spider) { return DEBUG.get(spider); }
    static double bodyLift(Debug debug,Quaternionf orientation,double previous) {
        if(debug==null)return 0;
        Vec3 up=vec(orientation.transform(new Vector3f(0,1,0))).normalize();
        // Error is measured after the current body transform: accumulate a
        // bounded correction rather than cancelling the previous frame's lift.
        double vertical=debug.stanceError().dot(up);
        double extension=debug.stanceError().subtract(up.scale(vertical)).lengthSqr();
        return Math.clamp(previous+(vertical-Math.min(.06,extension*.12))*.5/net.krodark.asterion.update.underworld.entity.SpiderDimensions.RENDER_SCALE,-.26,.26);
    }

    /** Fit the support triangle/plane, expressed in the un-tilted body frame. */
    static Vec3 bodyTilt(Debug debug,Quaternionf orientation) {
        if(debug==null)return Vec3.ZERO;
        var feet=debug.legs().stream().filter(Leg::contact).map(Leg::target).toList();
        if(feet.size()<3)return Vec3.ZERO;
        Vec3 center=Vec3.ZERO;
        for(Vec3 foot:feet)center=center.add(foot);
        center=center.scale(1.0/feet.size());
        Quaternionf inverse=new Quaternionf(orientation).conjugate();
        double xx=0,zz=0,xz=0,xy=0,zy=0;
        for(Vec3 foot:feet) {
            Vector3f p=inverse.transform(vector(foot.subtract(center)));
            xx+=p.x*p.x;zz+=p.z*p.z;xz+=p.x*p.z;xy+=p.x*p.y;zy+=p.z*p.y;
        }
        double determinant=xx*zz-xz*xz;
        if(determinant<.001)return Vec3.ZERO;
        double a=(xy*zz-zy*xz)/determinant,b=(zy*xx-xy*xz)/determinant;
        double pitch=-Math.atan(b)*.85,roll=Math.atan(a)*.85;
        // Even on a level floor the stance shifts while the other legs swing.
        // Follow that load transfer, rather than adding a time-based fake bob.
        Vector3f load=inverse.transform(vector(debug.stanceError()));
        pitch+=Math.clamp(-load.z*.16,-.07,.07);
        roll+=Math.clamp(load.x*.16,-.07,.07);
        double length=Math.hypot(pitch,roll),limit=Math.toRadians(16);
        double scale=length>limit?limit/length:1;
        return new Vec3(pitch*scale,0,roll*scale);
    }
    public static final class Memory {
        private final Vec3[] feet = new Vec3[8];
        private final Vec3[] starts = new Vec3[8];
        private final Vec3[] destinations = new Vec3[8];
        private final float[] stepStart = new float[8];
        private final Vector3f[][] rotations = new Vector3f[8][];
        private final Vec3[] contactNormals = new Vec3[8];
        private final float[] unsupportedSince = new float[8];
        private final Vec3[] probeTargets=new Vec3[8], probeNormals=new Vec3[8], probePositions=new Vec3[8];
        private final int[] probeTicks=new int[8];
        private final boolean[] probeContacts=new boolean[8];
        private float age = -1;
        private Vec3 origin;
        public Memory() { java.util.Arrays.fill(unsupportedSince,-1); }
    }
    private SpiderLegIK() { }

    public static void apply(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones, Frame frame) {
        // Remove the view/entity transform, retaining the exact scale, yaw and surface tilt
        // used by this render pass. This also works in third person and alternate cameras.
        Matrix4f model = new Matrix4f(pass.getPreRenderMatrixState()).invert()
                .mul(pass.getModelRenderMatrixState());
        Matrix4f world = new Matrix4f().translation((float)frame.origin.x, (float)frame.origin.y,
                (float)frame.origin.z).mul(model);
        Matrix4f inverse = new Matrix4f(world).invert();
        Memory memory = frame.memory;
        boolean update = frame.age != memory.age;
        boolean reset = memory.origin == null || memory.origin.distanceToSqr(frame.origin) > 9
                || frame.age < memory.age;
        Vec3 up = vec(world.transformDirection(new Vector3f(0, 1, 0))).normalize();
        Vec3 probeUp = frame.spider.hasSurfaceSupport() ? frame.spider.attachmentNormal().scale(-1) : up;
        List<Leg> debug = new ArrayList<>(8);
        Vec3 stanceError=Vec3.ZERO;
        int planted=0;
        for (int leg = 0; leg < 8; leg++) {
            BoneSnapshot[] chain = {bones.get(NAMES[leg]).orElse(null),
                    bones.get(NAMES[leg]+"mid").orElse(null), bones.get(NAMES[leg]+"end").orElse(null)};
            if (chain[0] == null || chain[1] == null || chain[2] == null) continue;
            Vector3f[] pivots = new Vector3f[3];
            Vector3f[] rest = new Vector3f[3];
            Vector3f[] angles = new Vector3f[3];
            for (int j = 0; j < 3; j++) {
                GeoBone bone = chain[j].getBone();
                pivots[j] = new Vector3f(bone.pivotX(),bone.pivotY(),bone.pivotZ()).div(16);
                rest[j] = new Vector3f(bone.baseRotX(),bone.baseRotY(),bone.baseRotZ());
                angles[j] = new Vector3f(rest[j]);
            }
            // Distal silhouette endpoint in the supplied model's coordinates (X is
            // mirrored by GeckoLib's Bedrock importer). Match the shortened distal plane.
            // The visible texture occupies 75% of the distal plane. Solve for
            // that visible foot, rather than its transparent geometry boundary.
            Vector3f tip = new Vector3f(pivots[2]).add(leg < 4 ? -13F/16 : 13F/16, -6F/16, 0);
            Vec3 nominal = vec(world.transformPosition(endpoint(pivots,angles,tip)));
            Vec3 restingFoot=nominal;
            double elapsed=Math.clamp(frame.age-memory.age,.05,2);
            Vec3 velocity=reset?Vec3.ZERO:frame.origin.subtract(memory.origin).scale(1/elapsed);
            Vec3 lead=velocity.subtract(probeUp.scale(velocity.dot(probeUp))).scale(1.8);
            if(lead.length()>.4)lead=lead.normalize().scale(.4);
            nominal=nominal.add(lead);
            boolean contact;
            Vec3 contactNormal,target;
            boolean probe=reset || frame.spider.onWeb() || memory.probePositions[leg]==null
                    || memory.probeTicks[leg]!=(int)frame.age
                    || memory.probePositions[leg].distanceToSqr(nominal)>.04;
            if(probe) {
            Vec3 start = nominal.add(probeUp.scale(1.2*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE));
            Vec3 end = nominal.subtract(probeUp.scale(1.6*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE));
            var hit = frame.spider.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,frame.spider));
            contact = hit.getType() != HitResult.Type.MISS;
            contactNormal = contact ? hit.getDirection().getUnitVec3() : probeUp;
            target = contact ? hit.getLocation().add(contactNormal.scale(.035)) : nominal;
            if(!contact) {
                // A wide foot may overhang the voxel stair recess. Search slightly
                // inward, within the leg's reach, instead of letting it dangle.
                Vec3 hip=vec(world.transformPosition(new Vector3f(pivots[0])));
                Vec3 inset=nominal.lerp(hip,.22);
                var nearby=frame.spider.level().clip(new ClipContext(inset.add(probeUp.scale(1.2*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE)),
                        inset.subtract(probeUp.scale(1.6*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                if(nearby.getType()!=HitResult.Type.MISS) {
                    contact=true; contactNormal=nearby.getDirection().getUnitVec3();
                    target=nearby.getLocation().add(contactNormal.scale(.035));
                }
            }
            // At a wall/floor junction the lower legs must also see the floor;
            // probing only toward the wall would let the entire distal segment sink.
            if (frame.spider.attachedSurface().getAxis().isHorizontal()) {
                Vec3 above = new Vec3(nominal.x,Math.max(nominal.y+1.5,frame.origin.y+1),nominal.z);
                var floor = frame.spider.level().clip(new ClipContext(above,nominal.add(0,-.2,0),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                if (floor.getType() != HitResult.Type.MISS && floor.getDirection() == net.minecraft.core.Direction.UP
                        && floor.getLocation().y > target.y) {
                    target = floor.getLocation().add(0,.035,0); contact = true; contactNormal=new Vec3(0,1,0);
                }
            }
            memory.probePositions[leg]=nominal;memory.probeTicks[leg]=(int)frame.age;
            memory.probeContacts[leg]=contact;memory.probeTargets[leg]=target;memory.probeNormals[leg]=contactNormal;
            } else {
                contact=memory.probeContacts[leg];target=memory.probeTargets[leg];contactNormal=memory.probeNormals[leg];
            }
            Vec3 silk = frame.spider.onWeb()?LimboWebWorldRenderer.spiderContact(nominal, 1.25*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE):null;
            if (silk != null && (!contact || silk.distanceToSqr(nominal) < target.distanceToSqr(nominal))) {
                target = silk.add(probeUp.scale(.025)); contact = true; contactNormal=probeUp;
            }
            if(!contact) {
                // Unsupported legs keep searching/curling instead of snapping
                // back to the authored static pose. Offset lives in body space.
                Vec3 reach=airborneOffset(leg,frame.age,frame.spider.getUUID().hashCode());
                target=nominal.add(vec(world.transformDirection(vector(reach))));
            }
            if (update) {
                boolean moving = memory.origin != null && memory.origin.distanceToSqr(frame.origin) > .00001;
                float gaitClock = frame.age + Math.floorMod(frame.spider.getUUID().hashCode(),47);
                boolean turn = ((int)(gaitClock / 2.5) & 1) == ((leg + leg / 4) & 1);
                Vec3 old = memory.feet[leg];
                Vec3 plantedNormal=memory.contactNormals[leg]==null?probeUp:memory.contactNormals[leg];
                boolean supported = old == null || memory.destinations[leg] != null
                        || frame.spider.level().clip(new ClipContext(old.add(plantedNormal.scale(.12)),old.subtract(plantedNormal.scale(.12)),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider)).getType() != HitResult.Type.MISS
                        || frame.spider.onWeb() && LimboWebWorldRenderer.spiderContact(old,.2) != null;
                if(supported)memory.unsupportedSince[leg]=-1;
                else if(memory.unsupportedSince[leg]<0)memory.unsupportedSince[leg]=frame.age;
                boolean released=!supported && frame.age-memory.unsupportedSince[leg]>=(frame.spider.onWeb()?0:2);
                // Follow a moving strand while planted, without repeatedly lifting the foot.
                if(frame.spider.onWeb() && old!=null && memory.destinations[leg]==null) {
                    Vec3 movingSilk=LimboWebWorldRenderer.spiderContact(old,.3);
                    if(movingSilk!=null){old=movingSilk.add(probeUp.scale(.025));memory.feet[leg]=old;}
                }
                if (reset || old == null || released || !contact && !frame.spider.hasSurfaceSupport() && !frame.spider.onGround() && !frame.spider.onWeb() || old.distanceToSqr(target) > 3.24*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE) {
                    memory.feet[leg] = !contact && old!=null && !reset
                            ? old.lerp(target,1-Math.pow(.35,Math.clamp(frame.age-memory.age,0,2))) : target;
                    memory.destinations[leg] = null;
                    memory.contactNormals[leg]=contactNormal;
                } else {
                    if (contact && memory.destinations[leg] == null && old.distanceToSqr(target) > (moving ? .09 : .06)
                            && (!moving || turn) && canLift(leg,memory.destinations)) {
                        memory.starts[leg] = old; memory.destinations[leg] = target; memory.stepStart[leg] = frame.age;
                        memory.contactNormals[leg]=contactNormal;
                    }
                    if (memory.destinations[leg] != null) {
                        double t = Math.clamp((frame.age-memory.stepStart[leg])/2.3,0,1);
                        // Keep reaching ahead during the lift/traverse phase;
                        // freeze the touchdown target for the final descent.
                        // Otherwise fast bodies outrun a target chosen at lift-off.
                        if(contact && t<.7)
                            memory.destinations[leg]=memory.destinations[leg].lerp(target,
                                    1-Math.pow(.3,Math.clamp(frame.age-memory.age,0,2)));
                        memory.feet[leg] = swingFoot(memory.starts[leg],memory.destinations[leg],probeUp,t);
                        // Check the actual terrain along the swing, not just its
                        // destination: a tread can lie above both endpoints.
                        Vec3 swing=memory.feet[leg];
                        var obstacle=frame.spider.level().clip(new ClipContext(swing.add(probeUp.scale(1.25*net.krodark.asterion.update.underworld.entity.SpiderDimensions.SIZE)),
                                swing,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                        if(obstacle.getType()!=HitResult.Type.MISS)
                            memory.feet[leg]=obstacle.getLocation().add(probeUp.scale(.06));
                        if (t >= 1) memory.destinations[leg] = null;
                    }
                }
                if(supported && memory.destinations[leg]==null)contact=true;
            }
            if (memory.feet[leg] != null) target = memory.feet[leg];
            Vector3f localTarget = inverse.transformPosition(vector(target));
            // Warm-start from last frame; coherent movement converges much
            // sooner than solving every limb from its rest angles every frame.
            if(!reset && memory.rotations[leg]!=null)
                for(int j=0;j<3;j++)angles[j].set(memory.rotations[leg][j]);
            solve(pivots,rest,angles,tip,localTarget);
            if (reset || memory.rotations[leg] == null) {
                memory.rotations[leg] = new Vector3f[]{new Vector3f(angles[0]),new Vector3f(angles[1]),new Vector3f(angles[2])};
            } else {
                float blend = 1F-(float)Math.pow(.22,Math.clamp(frame.age-memory.age,0,2));
                for (int j=0;j<3;j++) {
                    if (update) memory.rotations[leg][j].lerp(angles[j],blend);
                    angles[j].set(memory.rotations[leg][j]);
                }
            }
            // Smoothing is cosmetic; it must not drag an otherwise valid foot
            // below its contact plane while the body rises over a tread.
            if(contact) {
                Vec3 actual=vec(world.transformPosition(endpoint(pivots,angles,tip)));
                if(actual.subtract(target).dot(contactNormal)<-.015) {
                    solve(pivots,rest,angles,tip,localTarget);
                    for(int j=0;j<3;j++)memory.rotations[leg][j].set(angles[j]);
                }
            }
            for (int j=0;j<3;j++) chain[j].setRotation(angles[j].x-rest[j].x,
                    angles[j].y-rest[j].y,angles[j].z-rest[j].z);
            List<Vec3> joints = new ArrayList<>(4);
            Matrix4f transform = new Matrix4f();
            for (int j=0;j<3;j++) {
                joints.add(vec(world.transformPosition(transform.transformPosition(new Vector3f(pivots[j])))));
                rotate(transform,pivots[j],angles[j]);
            }
            Vec3 foot = vec(world.transformPosition(transform.transformPosition(new Vector3f(tip))));
            joints.add(foot);
            float error=(float)foot.distanceTo(target);
            debug.add(new Leg(List.copyOf(joints),target,
                    contact && memory.destinations[leg]==null && error<.10F,error));
            if(contact && memory.destinations[leg]==null && error<.2F) {
                stanceError=stanceError.add(target.subtract(restingFoot));planted++;
            }
        }
        memory.age = frame.age; memory.origin = frame.origin;
        DEBUG.put(frame.spider,new Debug(List.copyOf(debug),frame.age,planted>=3?stanceError.scale(1.0/planted):Vec3.ZERO));
    }

    static Vec3 airborneOffset(int leg,float age,int seed) {
        double phase=age*.31+leg*1.73+Math.floorMod(seed,47)*.19;
        double side=leg<4?-1:1;
        return new Vec3(side*(.12+.13*Math.sin(phase)),
                .16+.18*Math.sin(phase*.83+.7),.18*Math.cos(phase));
    }

    /** Do not lift the opposite four legs while a support group is still stepping. */
    static Vec3 swingFoot(Vec3 from,Vec3 to,Vec3 up,double t) {
        t=Math.clamp(t,0,1);
        double lift=Math.abs(to.subtract(from).dot(up))>.1?.18:.12;
        double highest=Math.max(from.dot(up),to.dot(up))+lift;
        Vec3 raisedFrom=from.add(up.scale(highest-from.dot(up)));
        Vec3 raisedTo=to.add(up.scale(highest-to.dot(up)));
        double phase=t<.3?t/.3:t<.7?(t-.3)/.4:(t-.7)/.3;
        double ease=phase*phase*(3-2*phase);
        return t<.3?from.lerp(raisedFrom,ease):t<.7?raisedFrom.lerp(raisedTo,ease):raisedTo.lerp(to,ease);
    }

    static boolean canLift(int leg,Vec3[] destinations) {
        int group=(leg+leg/4)&1;
        for(int i=0;i<destinations.length;i++)
            if(destinations[i]!=null && ((i+i/4)&1)!=group)return false;
        return true;
    }

    /** Bounded CCD preserves the authored knee bends and never stretches a segment. */
    static void solve(Vector3f[] pivots, Vector3f[] rest, Vector3f[] angles, Vector3f tip, Vector3f target) {
        for (int iteration=0;iteration<18;iteration++) {
            if (endpoint(pivots,angles,tip).distanceSquared(target) < .0004F) break;
            for (int j=2;j>=0;j--) {
                Matrix4f parent = new Matrix4f();
                for (int k=0;k<j;k++) rotate(parent,pivots[k],angles[k]);
                Matrix4f inv = new Matrix4f(parent).invert();
                Vector3f from = inv.transformPosition(endpoint(pivots,angles,tip)).sub(pivots[j]);
                Vector3f to = inv.transformPosition(new Vector3f(target)).sub(pivots[j]);
                if (from.lengthSquared()<1e-7 || to.lengthSquared()<1e-7) continue;
                Quaternionf correction = new Quaternionf().rotationTo(from.normalize(),to.normalize());
                new Quaternionf().identity().slerp(correction,.65F)
                        .mul(new Quaternionf().rotationZYX(angles[j].z,angles[j].y,angles[j].x))
                        .getEulerAnglesZYX(angles[j]);
                for (int axis=0;axis<3;axis++) {
                    float difference = angles[j].get(axis)-rest[j].get(axis);
                    difference = (float)Math.atan2(Math.sin(difference),Math.cos(difference));
                    angles[j].setComponent(axis,rest[j].get(axis)+Math.clamp(difference,-.85F,.85F));
                }
            }
        }
    }
    private static void rotate(Matrix4f matrix, Vector3f pivot, Vector3f angles) {
        matrix.translate(pivot).rotateZYX(angles.z,angles.y,angles.x).translate(-pivot.x,-pivot.y,-pivot.z);
    }
    private static Vector3f endpoint(Vector3f[] pivots, Vector3f[] angles, Vector3f tip) {
        Matrix4f matrix = new Matrix4f();
        for (int j=0;j<3;j++) rotate(matrix,pivots[j],angles[j]);
        return matrix.transformPosition(new Vector3f(tip));
    }
    private static Vec3 vec(Vector3f v) { return new Vec3(v.x,v.y,v.z); }
    private static Vector3f vector(Vec3 v) { return new Vector3f((float)v.x,(float)v.y,(float)v.z); }
}

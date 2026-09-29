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
    static double bodyLift(Debug debug,Quaternionf orientation,double previous,double modelScale) {
        if(debug==null)return 0;
        Vec3 up=vec(orientation.transform(new Vector3f(0,1,0))).normalize();
        // Error is measured after the current body transform: accumulate a
        // bounded correction rather than cancelling the previous frame's lift.
        double vertical=debug.stanceError().dot(up);
        double extension=debug.stanceError().subtract(up.scale(vertical)).lengthSqr();
        return Math.clamp(previous+(vertical-Math.min(.06,extension*.12))*.5/modelScale,-.26,.26);
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
        private final float[] stepDuration = new float[8];
        private final Gait gait = new Gait();
        private Vec3 velocity = Vec3.ZERO;
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
        // Keep matrix math near the entity; absolute world coordinates lose
        // sub-block precision in floats and make planted feet vibrate.
        Matrix4f world = model;
        Matrix4f inverse = new Matrix4f(world).invert();
        Memory memory = frame.memory;
        boolean update = frame.age != memory.age;
        boolean reset = memory.origin == null || memory.origin.distanceToSqr(frame.origin) > 9
                || frame.age < memory.age || frame.age-memory.age > 5;
        if (update) {
            double elapsed = Math.max(.0001,frame.age-memory.age);
            Vec3 measured=reset?Vec3.ZERO:frame.origin.subtract(memory.origin).scale(1/elapsed);
            // Network catch-up and support settling must not kick the feet ahead.
            if(measured.length()>.6)measured=measured.normalize().scale(.6);
            memory.velocity=reset?Vec3.ZERO:memory.velocity.lerp(measured,1-Math.pow(.35,elapsed));
            if (reset) java.util.Arrays.fill(memory.destinations,null);
            memory.gait.beginFrame(memory.destinations);
        }
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
            Vec3 nominal = toWorld(world,endpoint(pivots,angles,tip),frame.origin);
            Vec3 restingFoot=nominal;
            Vec3 velocity=memory.velocity;
            Vec3 lead=velocity.subtract(probeUp.scale(velocity.dot(probeUp))).scale(2.8);
            if(lead.length()>.4)lead=lead.normalize().scale(.4);
            nominal=nominal.add(lead);
            boolean contact;
            Vec3 contactNormal,target;
            boolean probe=reset || frame.spider.onWeb() || memory.probePositions[leg]==null
                    || memory.probeTicks[leg]!=(int)frame.age
                    || memory.probePositions[leg].distanceToSqr(nominal)>.04;
            if(probe) {
            Vec3 start = nominal.add(probeUp.scale(1.2*frame.spider.spiderScale()));
            Vec3 end = nominal.subtract(probeUp.scale(1.6*frame.spider.spiderScale()));
            var hit = frame.spider.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,frame.spider));
            contact = hit.getType() != HitResult.Type.MISS;
            contactNormal = contact ? hit.getDirection().getUnitVec3() : probeUp;
            target = contact ? hit.getLocation().add(contactNormal.scale(.035)) : nominal;
            if(!contact) {
                // A wide foot may overhang the voxel stair recess. Search slightly
                // inward, within the leg's reach, instead of letting it dangle.
                Vec3 hip=toWorld(world,new Vector3f(pivots[0]),frame.origin);
                for(double insetAmount:new double[]{.22,.45}) {
                    Vec3 inset=nominal.lerp(hip,insetAmount);
                    var nearby=frame.spider.level().clip(new ClipContext(inset.add(probeUp.scale(1.2*frame.spider.spiderScale())),
                            inset.subtract(probeUp.scale(1.6*frame.spider.spiderScale())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                    if(nearby.getType()!=HitResult.Type.MISS) {
                        contact=true; contactNormal=nearby.getDirection().getUnitVec3();
                        target=nearby.getLocation().add(contactNormal.scale(.035));break;
                    }
                }
                if(!contact) {
                    // On convex corners a neighboring face is outside the current
                    // normal ray. Reach from the hip toward the searching foot.
                    Vec3 reach=nominal.subtract(hip).normalize();
                    var edge=frame.spider.level().clip(new ClipContext(hip,nominal.add(reach.scale(.25)),
                            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                    if(edge.getType()!=HitResult.Type.MISS) {
                        contact=true;contactNormal=edge.getDirection().getUnitVec3();
                        target=edge.getLocation().add(contactNormal.scale(.035));
                    }
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
            Vec3 silk = frame.spider.onWeb()?LimboWebWorldRenderer.spiderContact(nominal, 1.25*frame.spider.spiderScale()):null;
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
                Vec3 old = memory.feet[leg];
                Vec3 plantedNormal=memory.contactNormals[leg]==null?probeUp:memory.contactNormals[leg];
                boolean supported = old != null && (memory.destinations[leg] != null
                        || frame.spider.level().clip(new ClipContext(old.add(plantedNormal.scale(.12)),old.subtract(plantedNormal.scale(.12)),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider)).getType() != HitResult.Type.MISS
                        || frame.spider.onWeb() && LimboWebWorldRenderer.spiderContact(old,.2) != null);
                if(supported)memory.unsupportedSince[leg]=-1;
                else if(memory.unsupportedSince[leg]<0)memory.unsupportedSince[leg]=frame.age;
                boolean released=!supported && frame.age-memory.unsupportedSince[leg]>=(frame.spider.onWeb()?0:2);
                // Follow a moving strand while planted, without repeatedly lifting the foot.
                if(frame.spider.onWeb() && old!=null && memory.destinations[leg]==null) {
                    Vec3 movingSilk=LimboWebWorldRenderer.spiderContact(old,.3);
                    if(movingSilk!=null){old=movingSilk.add(probeUp.scale(.025));memory.feet[leg]=old;}
                }
                if (reset || old == null || !contact && (released || !frame.spider.hasSurfaceSupport() && !frame.spider.onGround() && !frame.spider.onWeb())
                        || old.distanceToSqr(target) > 3.24*frame.spider.spiderScale()*frame.spider.spiderScale()) {
                    memory.feet[leg] = !contact && old!=null && !reset
                            ? old.lerp(target,1-Math.pow(.35,Math.clamp(frame.age-memory.age,0,2))) : target;
                    memory.destinations[leg] = null;
                    memory.contactNormals[leg]=contactNormal;
                } else {
                    if (contact && memory.destinations[leg] == null && (released || needsStep(old,target,probeUp,frame.spider.spiderScale()))
                            && memory.gait.canStart(leg)) {
                        memory.starts[leg] = old; memory.destinations[leg] = target; memory.stepStart[leg] = frame.age;
                        memory.stepDuration[leg] = (float)Math.clamp(.65*frame.spider.spiderScale()/Math.max(.01,velocity.length()),2.4,4);
                    }
                    if (memory.destinations[leg] != null) {
                        double t = Math.clamp((frame.age-memory.stepStart[leg])/memory.stepDuration[leg],0,1);
                        // Keep reaching ahead during the lift/traverse phase;
                        // freeze the touchdown target for the final descent.
                        // Otherwise fast bodies outrun a target chosen at lift-off.
                        if(contact && t<.7)
                            memory.destinations[leg]=memory.destinations[leg].lerp(target,
                                    1-Math.pow(.3,Math.clamp(frame.age-memory.age,0,2)));
                        Vec3 liftUp=plantedNormal.lerp(contactNormal,t*t*(3-2*t));
                        if(liftUp.lengthSqr()<.01)liftUp=probeUp;
                        memory.feet[leg] = swingFoot(memory.starts[leg],memory.destinations[leg],liftUp.normalize(),t,frame.spider.spiderScale());
                        // Check the actual terrain along the swing, not just its
                        // destination: a tread can lie above both endpoints.
                        Vec3 swing=memory.feet[leg];
                        var obstacle=frame.spider.level().clip(new ClipContext(swing.add(probeUp.scale(1.25*frame.spider.spiderScale())),
                                swing,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,frame.spider));
                        if(obstacle.getType()!=HitResult.Type.MISS)
                            memory.feet[leg]=obstacle.getLocation().add(probeUp.scale(.06));
                        if (t >= 1) {
                            memory.destinations[leg] = null;
                            memory.contactNormals[leg]=contactNormal;
                        }
                    }
                }
                if(!supported && memory.destinations[leg]==null && old!=null && !reset)contact=false;
                if(supported && memory.destinations[leg]==null) {
                    contact=true;contactNormal=memory.contactNormals[leg]==null?probeUp:memory.contactNormals[leg];
                }
            }
            if (memory.feet[leg] != null) target = memory.feet[leg];
            Vector3f localTarget = inverse.transformPosition(vector(target.subtract(frame.origin)));
            // Warm-start from last frame; coherent movement converges much
            // sooner than solving every limb from its rest angles every frame.
            if(!reset && memory.rotations[leg]!=null)
                for(int j=0;j<3;j++)angles[j].set(memory.rotations[leg][j]);
            solve(pivots,rest,angles,tip,localTarget);
            if (reset || memory.rotations[leg] == null) {
                memory.rotations[leg] = new Vector3f[]{new Vector3f(angles[0]),new Vector3f(angles[1]),new Vector3f(angles[2])};
            } else {
                // The swing target is already eased. Filtering joint angles again
                // erases most of the short lift and drags the rendered foot along
                // the ground while the gait believes it is airborne.
                float blend = contact ? 1F : 1F-(float)Math.pow(.22,Math.clamp(frame.age-memory.age,0,2));
                for (int j=0;j<3;j++) {
                    if (update) memory.rotations[leg][j].lerp(angles[j],blend);
                    angles[j].set(memory.rotations[leg][j]);
                }
            }
            // Smoothing is cosmetic; it must not drag an otherwise valid foot
            // below its contact plane while the body rises over a tread.
            if(contact && memory.destinations[leg]==null) {
                Vec3 actual=toWorld(world,endpoint(pivots,angles,tip),frame.origin);
                if(actual.distanceToSqr(target)>.000225) {
                    solve(pivots,rest,angles,tip,localTarget);
                    for(int j=0;j<3;j++)memory.rotations[leg][j].set(angles[j]);
                }
            }
            for (int j=0;j<3;j++) chain[j].setRotation(angles[j].x-rest[j].x,
                    angles[j].y-rest[j].y,angles[j].z-rest[j].z);
            List<Vec3> joints = new ArrayList<>(4);
            Matrix4f transform = new Matrix4f();
            for (int j=0;j<3;j++) {
                joints.add(toWorld(world,transform.transformPosition(new Vector3f(pivots[j])),frame.origin));
                rotate(transform,pivots[j],angles[j]);
            }
            Vec3 foot = toWorld(world,transform.transformPosition(new Vector3f(tip)),frame.origin);
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

    /** Admit one complete support group per swing, then give the other group priority.
     * A wall-clock phase and per-leg admission can continually restart the first
     * group before the second group gets a chance to lift. */
    static final class Gait {
        private int nextGroup;
        private int admittedGroup = -1;
        void beginFrame(Vec3[] destinations) {
            admittedGroup = -1;
            for (Vec3 destination : destinations) if (destination != null) return;
            admittedGroup = nextGroup;
            nextGroup ^= 1;
        }
        boolean canStart(int leg) { return ((leg+leg/4)&1) == admittedGroup; }
    }

    static boolean needsStep(Vec3 planted,Vec3 target,Vec3 up,double scale) {
        Vec3 offset=target.subtract(planted);
        double rise=offset.dot(up);
        // Small body-height adjustments must not cause shuffling on level ground.
        return offset.subtract(up.scale(rise)).lengthSqr()>.0484*scale*scale
                || Math.abs(rise)>.18*scale;
    }

    /** Ease through a walking arc; lift before crossing a raised tread. */
    static Vec3 swingFoot(Vec3 from,Vec3 to,Vec3 up,double t) {
        return swingFoot(from,to,up,t,1);
    }

    static Vec3 swingFoot(Vec3 from,Vec3 to,Vec3 up,double t,double scale) {
        t=Math.clamp(t,0,1);
        if(Math.abs(to.subtract(from).dot(up))<=.1*scale) {
            double arc=Math.sin(Math.PI*t);
            return from.lerp(to,t*t*(3-2*t)).add(up.scale(.20*scale*arc*arc));
        }
        double lift=.24*scale;
        double highest=Math.max(from.dot(up),to.dot(up))+lift;
        Vec3 raisedFrom=from.add(up.scale(highest-from.dot(up)));
        Vec3 raisedTo=to.add(up.scale(highest-to.dot(up)));
        double phase=t<.3?t/.3:t<.7?(t-.3)/.4:(t-.7)/.3;
        double ease=phase*phase*(3-2*phase);
        return t<.3?from.lerp(raisedFrom,ease):t<.7?raisedFrom.lerp(raisedTo,ease):raisedTo.lerp(to,ease);
    }

    /** Bounded CCD preserves the authored knee bends and never stretches a segment. */
    static void solve(Vector3f[] pivots, Vector3f[] rest, Vector3f[] angles, Vector3f tip, Vector3f target) {
        Vector3f[] best={new Vector3f(angles[0]),new Vector3f(angles[1]),new Vector3f(angles[2])};
        float bestError=endpoint(pivots,angles,tip).distanceSquared(target);
        for (int iteration=0;iteration<24;iteration++) {
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
            float error=endpoint(pivots,angles,tip).distanceSquared(target);
            if(error<bestError) {
                bestError=error;
                for(int j=0;j<3;j++)best[j].set(angles[j]);
            }
        }
        for(int j=0;j<3;j++)angles[j].set(best[j]);
    }
    private static void rotate(Matrix4f matrix, Vector3f pivot, Vector3f angles) {
        matrix.translate(pivot).rotateZYX(angles.z,angles.y,angles.x).translate(-pivot.x,-pivot.y,-pivot.z);
    }
    private static Vector3f endpoint(Vector3f[] pivots, Vector3f[] angles, Vector3f tip) {
        Matrix4f matrix = new Matrix4f();
        for (int j=0;j<3;j++) rotate(matrix,pivots[j],angles[j]);
        return matrix.transformPosition(new Vector3f(tip));
    }
    static Vec3 toWorld(Matrix4f model,Vector3f local,Vec3 origin) {
        return vec(model.transformPosition(new Vector3f(local))).add(origin);
    }
    private static Vec3 vec(Vector3f v) { return new Vec3(v.x,v.y,v.z); }
    private static Vector3f vector(Vec3 v) { return new Vector3f((float)v.x,(float)v.y,(float)v.z); }
}

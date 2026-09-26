package net.krodark.asterion.update.underworld.client;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.BoneSnapshots;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.render.entity.SurfaceOrientation;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.krodark.asterion.update.underworld.entity.SpiderSurfaceMotion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.WeakHashMap;

/** The mimic uses the dead person's silhouette until the spider reveals itself. */
public final class LimboSpiderRenderer extends GeoEntityRenderer<LimboSpiderEntity, EntityRenderState> {
    private static final DataTicket<Boolean> MIMIC = DataTickets.create("asterion_spider_mimic",Boolean.class);
    private static final DataTicket<Float> ROT_X = DataTickets.create("asterion_spider_rot_x",Float.class);
    private static final DataTicket<Float> ROT_Y = DataTickets.create("asterion_spider_rot_y",Float.class);
    private static final DataTicket<Float> ROT_Z = DataTickets.create("asterion_spider_rot_z",Float.class);
    private static final DataTicket<Float> ROT_W = DataTickets.create("asterion_spider_rot_w",Float.class);
    private static final DataTicket<Float> LOWER = DataTickets.create("asterion_spider_lower",Float.class);
    private static final DataTicket<SpiderLegIK.Frame> IK = DataTickets.create("asterion_spider_ik",SpiderLegIK.Frame.class);
    private static final DataTicket<Vec3> SILK_OFFSET = DataTickets.create("asterion_spider_silk_offset",Vec3.class);
    private final Map<LimboSpiderEntity,SurfacePose> poses = new WeakHashMap<>();
    public LimboSpiderRenderer(EntityRendererProvider.Context context) {
        super(context,new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) {
                return Asterion.id(state.getOrDefaultGeckolibData(MIMIC,false)
                        ? "entity/wanderer" : "entity/spider");
            }
            @Override public Identifier getTextureResource(GeoRenderState state) {
                return Asterion.id(state.getOrDefaultGeckolibData(MIMIC,false)
                        ? "textures/entity/wanderer.png" : "textures/entity/spider.png");
            }
            @Override public Identifier getAnimationResource(LimboSpiderEntity entity) {
                return Asterion.id("entity/spider");
            }
        });
        withScale(.7F);
        shadowRadius = .7F;
    }
    @Override public void addRenderData(LimboSpiderEntity spider, Void related,
                                        EntityRenderState state, float partialTick) {
        state.addGeckolibData(MIMIC, spider.state() == LimboSpiderEntity.State.MIMICKING);
        Direction face = spider.attachedSurface();
        float yaw = calculateYRot(spider,0,partialTick);
        SurfacePose pose = poses.computeIfAbsent(spider,ignored -> new SurfacePose());
        float age = spider.tickCount + partialTick;
        float delta = Math.clamp(age - pose.age, 0F, 2F);
        Vec3 position = spider.getPosition(partialTick);
        state.addGeckolibData(SILK_OFFSET,LimboWebWorldRenderer.spiderOffset(spider,position));
        Vec3 normal = spider.attachmentNormal();
        float lower=.30F+(float)Math.clamp(1-normal.dot(face.getUnitVec3()),0,.35)*.7F;
        pose.lower=pose.position==null?lower:pose.lower+(lower-pose.lower)*(1F-(float)Math.pow(.7,delta));
        // Server locomotion is authoritative. Interpolated positions include
        // collision corrections, settling and network catch-up, not just gait.
        Vec3 forward=spider.locomotionHeading();
        Vec3 tangent = forward.subtract(normal.scale(forward.dot(normal)));
        if (pose.face != face) pose.heading = SpiderSurfaceMotion.cornerHeading(pose.face,face,pose.heading);
        // Ignore settling motion and average voxel-route turns before facing
        // them. A short sideways route segment should not swivel the whole body.
        if (tangent.lengthSqr() > .0025)
            pose.heading = tangent.normalize();
        pose.heading = pose.heading.subtract(normal.scale(pose.heading.dot(normal)));
        if (pose.heading.lengthSqr() < .001) pose.heading = SpiderSurfaceMotion.heading(face,Vec3.ZERO,Vec3.ZERO,false);
        pose.heading = pose.heading.normalize();
        Quaternionf base = new Quaternionf().rotationY((float)Math.toRadians(180-yaw));
        Quaternionf target = new Quaternionf(base).mul(SurfaceOrientation.beetleSurfaceRotation(
                normal,pose.heading,yaw));
        // Smooth in world space. Smoothing a yaw-relative quaternion while vanilla
        // changes body yaw makes a motionless wall spider spin and snap back.
        if (pose.position == null) pose.orientation.set(target);
        else {
            float angle=2F*(float)Math.acos(Math.clamp(Math.abs(pose.orientation.dot(target)),0F,1F));
            float blend=1F-(float)Math.pow(.30,delta);
            if(angle>.001F)blend=Math.min(blend,(float)Math.toRadians(24)*delta/angle);
            pose.orientation.slerp(target,blend).normalize();
        }
        SpiderLegIK.Debug contacts=SpiderLegIK.debug(spider);
        boolean planted=spider.state()!=LimboSpiderEntity.State.MIMICKING && contacts!=null
                && age-contacts.age()<3 && age>=contacts.age()
                && contacts.legs().stream().filter(SpiderLegIK.Leg::contact).count()>=3;
        if(planted)pose.lastSupportAge=age;
        // Hold through a brief support-group handoff instead of snapping the
        // torso flat every time one foot lifts. Airborne bodies relax smoothly.
        Vec3 lean=planted?SpiderLegIK.bodyTilt(contacts,pose.orientation)
                :age-pose.lastSupportAge<3?pose.lean:Vec3.ZERO;
        double lift=planted?SpiderLegIK.bodyLift(contacts,pose.orientation,pose.lift)
                :age-pose.lastSupportAge<3?pose.lift:0;
        pose.lean=pose.lean.lerp(lean,1-Math.pow(.65,delta));
        pose.lift+=(lift-pose.lift)*(1-Math.pow(.6,delta));
        state.addGeckolibData(LOWER,pose.lower-(float)pose.lift);
        pose.position = position;
        pose.face = face;
        pose.age = age;
        Quaternionf relative = new Quaternionf(base).conjugate().mul(pose.orientation);
        // Foot-driven torso pose, capped at sixteen degrees; never feed it into heading,
        // attachment normals, collision, navigation, or the behavior state machine.
        relative.rotateX((float)pose.lean.x).rotateZ((float)pose.lean.z);
        state.addGeckolibData(ROT_X,relative.x);
        state.addGeckolibData(ROT_Y,relative.y);
        state.addGeckolibData(ROT_Z,relative.z);
        state.addGeckolibData(ROT_W,relative.w);
        state.addGeckolibData(IK, new SpiderLegIK.Frame(spider, spider.getPosition(partialTick), age, pose.legs));
    }
    @Override public void preRenderPass(RenderPassInfo<EntityRenderState> pass,net.minecraft.client.renderer.SubmitNodeCollector output) {
        super.preRenderPass(pass,output);
        if (pass.getOrDefaultGeckolibData(MIMIC,false)) return;
        Vec3 offset=pass.getOrDefaultGeckolibData(SILK_OFFSET,Vec3.ZERO);
        pass.poseStack().translate(offset.x,offset.y,offset.z);
    }
    @Override public void adjustRenderPose(RenderPassInfo<EntityRenderState> pass) {
        super.adjustRenderPose(pass);
        if (pass.getOrDefaultGeckolibData(MIMIC,false)) return;
        // Rotate around the model's body, not its feet. This keeps the abdomen
        // inside the collision body while turning onto a wall or ceiling.
        pass.poseStack().translate(0,.84,0);
        pass.poseStack().mulPose(new Quaternionf(
                pass.getOrDefaultGeckolibData(ROT_X,0F),
                pass.getOrDefaultGeckolibData(ROT_Y,0F),
                pass.getOrDefaultGeckolibData(ROT_Z,0F),
                pass.getOrDefaultGeckolibData(ROT_W,1F)).normalize());
        pass.poseStack().translate(0,-.84,0);
        // Bring the abdomen closer to the support without shrinking the legs or
        // moving the physical collision box into the stair envelope.
        pass.poseStack().translate(0,-pass.getOrDefaultGeckolibData(LOWER,.30F),0);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass,
                                                     BoneSnapshots bones) {
        if (pass.getOrDefaultGeckolibData(MIMIC,false)) return;
        SpiderLegIK.Frame frame = pass.getGeckolibData(IK);
        if (frame != null) SpiderLegIK.apply(pass, bones, frame);
    }
    private static final class SurfacePose {
        private final Quaternionf orientation = new Quaternionf();
        private float age;
        private float lower;
        private double lift;
        private float lastSupportAge=-100;
        private Vec3 lean=Vec3.ZERO;
        private Vec3 position;
        private Vec3 heading = new Vec3(0,0,1);
        private Direction face = Direction.DOWN;
        private final SpiderLegIK.Memory legs = new SpiderLegIK.Memory();
    }
}

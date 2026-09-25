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
        Vec3 moved = pose.position == null || delta == 0 ? Vec3.ZERO : position.subtract(pose.position).scale(1/delta);
        Vec3 normal = spider.attachmentNormal();
        float lower=.30F+(float)Math.clamp(1-normal.dot(face.getUnitVec3()),0,.35)*.7F;
        pose.lower=pose.position==null?lower:pose.lower+(lower-pose.lower)*(1F-(float)Math.pow(.7,delta));
        state.addGeckolibData(LOWER,pose.lower);
        Vec3 tangent = moved.subtract(normal.scale(moved.dot(normal)));
        if (pose.face != face) pose.heading = SpiderSurfaceMotion.cornerHeading(pose.face,face,pose.heading);
        // Ignore settling motion and average voxel-route turns before facing
        // them. A short sideways route segment should not swivel the whole body.
        if (tangent.lengthSqr() > .0025)
            pose.heading = pose.heading.lerp(tangent.normalize(),1-Math.pow(.88,delta));
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
            float blend=1F-(float)Math.pow(.85,delta);
            if(angle>.001F)blend=Math.min(blend,(float)Math.toRadians(6)*delta/angle);
            pose.orientation.slerp(target,blend).normalize();
        }
        SpiderLegIK.Debug contacts=SpiderLegIK.debug(spider);
        Vec3 lean=spider.state()!=LimboSpiderEntity.State.MIMICKING && contacts!=null
                && age-contacts.age()<3 && age>=contacts.age()
                ? SpiderLegIK.bodyTilt(contacts,pose.orientation):Vec3.ZERO;
        pose.lean=pose.lean.lerp(lean.scale(.35),1-Math.pow(.9,delta));
        pose.position = position;
        pose.face = face;
        pose.age = age;
        Quaternionf relative = new Quaternionf(base).conjugate().mul(pose.orientation);
        // Render-only, capped at four degrees; never feed this lean into heading,
        // attachment normals, collision, navigation, or the behavior state machine.
        relative.rotateX((float)pose.lean.x).rotateZ((float)pose.lean.z);
        state.addGeckolibData(ROT_X,relative.x);
        state.addGeckolibData(ROT_Y,relative.y);
        state.addGeckolibData(ROT_Z,relative.z);
        state.addGeckolibData(ROT_W,relative.w);
        state.addGeckolibData(IK, new SpiderLegIK.Frame(spider, spider.getPosition(partialTick), age, pose.legs));
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
        private Vec3 lean=Vec3.ZERO;
        private Vec3 position;
        private Vec3 heading = new Vec3(0,0,1);
        private Direction face = Direction.DOWN;
        private final SpiderLegIK.Memory legs = new SpiderLegIK.Memory();
    }
}

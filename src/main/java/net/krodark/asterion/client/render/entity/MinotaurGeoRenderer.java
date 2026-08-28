package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MinotaurGeoRenderer extends GeoEntityRenderer<MinotaurEntity, EntityRenderState> {
    private static final DataTicket<Float> LOOK_YAW = DataTickets.create("asterion_minotaur_look_yaw", Float.class);
    private static final DataTicket<Float> LOOK_PITCH = DataTickets.create("asterion_minotaur_look_pitch", Float.class);
    private static final DataTicket<Integer> EYE_TINT = DataTickets.create("asterion_minotaur_eye_tint", Integer.class);
    private static final DataTicket<Float> GRAB_WEIGHT = DataTickets.create("asterion_minotaur_grab_weight", Float.class);
    private static final DataTicket<Float> GRAB_YAW = DataTickets.create("asterion_minotaur_grab_yaw", Float.class);
    private static final DataTicket<Float> GRAB_PITCH = DataTickets.create("asterion_minotaur_grab_pitch", Float.class);
    private final Map<UUID, LookPose> lookPoses = new HashMap<>();
    private final Map<UUID, GrabPose> grabPoses = new HashMap<>();

    public MinotaurGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new MinotaurGeoModel());
        withScale(1.0F);
        withRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            protected net.minecraft.resources.Identifier getTextureResource(EntityRenderState state) {
                return Asterion.id("textures/entity/minotaur_eyes.png");
            }

            @Override
            public void submitRenderTask(RenderPassInfo<EntityRenderState> pass,
                                         SubmitNodeCollector renderTasks) {
                int original = pass.renderState().getOrDefaultGeckolibData(
                        DataTickets.RENDER_COLOR, 0xFFFFFFFF);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR,
                        pass.getOrDefaultGeckolibData(EYE_TINT, 0xFFFFFFFF));
                super.submitRenderTask(pass, renderTasks);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR, original);
            }
        });
        this.shadowRadius = 1.9F;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<EntityRenderState> pass, float width, float height) {
        float scale = 0.47F * AsterionConfig.INSTANCE.minotaurScale;
        super.scaleModelForRender(pass, width * scale, height * scale);
    }

    @Override
    public void addRenderData(MinotaurEntity minotaur, Void relatedObject,
                              EntityRenderState state, float partialTick) {
        Player player = Minecraft.getInstance().player;
        float targetYaw = 0.0F;
        float targetPitch = 0.0F;
        if (player != null && player.level() == minotaur.level() && player.isAlive()
                && minotaur.distanceToSqr(player) < 96.0D * 96.0D) {
            Vec3 origin = minotaur.getEyePosition(partialTick);
            Vec3 target = player.getEyePosition(partialTick);
            Vec3 delta = target.subtract(origin);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float worldYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
            float bodyYaw = Mth.rotLerp(partialTick, minotaur.yBodyRotO, minotaur.yBodyRot);
            targetYaw = Mth.clamp(Mth.wrapDegrees(worldYaw - bodyYaw), -72.0F, 72.0F);
            targetPitch = Mth.clamp((float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG),
                    -34.0F, 42.0F);
        }

        LookPose pose = lookPoses.computeIfAbsent(minotaur.getUUID(), ignored -> new LookPose());
        // Exponential smoothing is stable at any frame rate and prevents wrap-around snapping.
        float frameTicks = Math.max(0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        float blend = 1.0F - (float)Math.pow(0.72D, frameTicks);
        pose.yaw += Mth.wrapDegrees(targetYaw - pose.yaw) * blend;
        pose.pitch += (targetPitch - pose.pitch) * blend;
        state.addGeckolibData(LOOK_YAW, pose.yaw * Mth.DEG_TO_RAD);
        state.addGeckolibData(LOOK_PITCH, pose.pitch * Mth.DEG_TO_RAD);

        GrabPose grab = grabPoses.computeIfAbsent(minotaur.getUUID(), ignored -> new GrabPose());
        int grabTicks = minotaur.grabAttackTicks();
        float desiredGrab = 0.0F;
        float grabYaw = 0.0F;
        float grabPitch = 0.0F;
        if (minotaur.isPerformingGrab()) {
            desiredGrab = grabTicks < 11 ? smoother(grabTicks / 11.0F)
                    : grabTicks < 29 ? 1.0F : 1.0F - smoother((grabTicks - 29) / 11.0F);
            Entity targetEntity = minotaur.level().getEntity(minotaur.grabTargetEntityId());
            if (targetEntity != null) {
                Vec3 shoulderCenter = minotaur.position().add(0.0D, minotaur.getBbHeight() * 0.68D, 0.0D);
                Vec3 targetCenter = targetEntity.position().add(0.0D, targetEntity.getBbHeight() * 0.52D, 0.0D);
                Vec3 delta = targetCenter.subtract(shoulderCenter);
                double horizontal = Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
                float worldYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
                float bodyYaw = Mth.rotLerp(partialTick, minotaur.yBodyRotO, minotaur.yBodyRot);
                grabYaw = Mth.clamp(Mth.wrapDegrees(worldYaw - bodyYaw), -38.0F, 38.0F) * Mth.DEG_TO_RAD;
                grabPitch = Mth.clamp((float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG),
                        -28.0F, 34.0F) * Mth.DEG_TO_RAD;
            }
        }
        float grabBlend = 1.0F - (float)Math.pow(0.58D, frameTicks);
        grab.weight += (desiredGrab - grab.weight) * grabBlend;
        grab.yaw += (grabYaw - grab.yaw) * grabBlend;
        grab.pitch += (grabPitch - grab.pitch) * grabBlend;
        state.addGeckolibData(GRAB_WEIGHT, grab.weight);
        state.addGeckolibData(GRAB_YAW, grab.yaw);
        state.addGeckolibData(GRAB_PITCH, grab.pitch);
        float damage = minotaur.bossDamageFraction();
        if (minotaur.isExtremeBoss() || damage > 0.0F) {
            int greenBlue = Mth.floor(Mth.lerp(damage, 40.0F, 7.0F));
            state.addGeckolibData(EYE_TINT, 0xFFFF0000 | greenBlue << 8 | greenBlue);
        } else state.addGeckolibData(EYE_TINT, 0xFFFFFFFF);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        float yaw = pass.getOrDefaultGeckolibData(LOOK_YAW, 0.0F);
        float pitch = pass.getOrDefaultGeckolibData(LOOK_PITCH, 0.0F);
        // Bedrock/Gecko model axes face opposite the world-space look convention used above.
        rotateBone(bones, "body", -yaw * 0.18F, -pitch * 0.14F);
        rotateBone(bones, "neck", -yaw * 0.32F, -pitch * 0.31F);
        rotateBone(bones, "head", -yaw * 0.50F, -pitch * 0.55F);

        float grab = pass.getOrDefaultGeckolibData(GRAB_WEIGHT, 0.0F);
        if (grab > 0.001F) {
            float targetYaw = pass.getOrDefaultGeckolibData(GRAB_YAW, 0.0F);
            float targetPitch = pass.getOrDefaultGeckolibData(GRAB_PITCH, 0.0F);
            // Symmetric two-arm reach layered over the authored grab. Shoulders follow the target,
            // upper arms close the wide resting silhouette, elbows bend inward, and hands cup the
            // player's torso. Every link is eased from live render data, preventing snapping.
            rotateBone3(bones, "leftshoulder", targetPitch * 0.18F * grab,
                    -targetYaw * 0.58F * grab, -0.16F * grab);
            rotateBone3(bones, "rightshoulder", targetPitch * 0.18F * grab,
                    -targetYaw * 0.58F * grab, 0.16F * grab);
            rotateBone3(bones, "leftarm", (-0.92F + targetPitch * 0.52F) * grab,
                    (-0.28F - targetYaw * 0.36F) * grab, -0.72F * grab);
            rotateBone3(bones, "rightarm", (-0.92F + targetPitch * 0.52F) * grab,
                    (0.28F - targetYaw * 0.36F) * grab, 0.72F * grab);
            rotateBone3(bones, "lowerleftarm", -0.62F * grab,
                    (-0.18F - targetYaw * 0.16F) * grab, 0.46F * grab);
            rotateBone3(bones, "lowerrightarm", -0.62F * grab,
                    (0.18F - targetYaw * 0.16F) * grab, -0.46F * grab);
            rotateBone3(bones, "lefthand", -0.16F * grab, -0.32F * grab, 0.24F * grab);
            rotateBone3(bones, "righthand", -0.16F * grab, 0.32F * grab, -0.24F * grab);
            rotateBone3(bones, "body", targetPitch * 0.12F * grab,
                    -targetYaw * 0.18F * grab, 0.0F);
        }
    }

    private static void rotateBone(BoneSnapshots bones, String name, float yaw, float pitch) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(
                snapshot.getRotX() + pitch, snapshot.getRotY() + yaw, snapshot.getRotZ()));
    }

    private static void rotateBone3(BoneSnapshots bones, String name, float pitch, float yaw, float roll) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(snapshot.getRotX() + pitch,
                snapshot.getRotY() + yaw, snapshot.getRotZ() + roll));
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static final class LookPose {
        private float yaw;
        private float pitch;
    }

    private static final class GrabPose {
        private float weight;
        private float yaw;
        private float pitch;
    }
}

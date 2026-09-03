package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
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
    private static final DataTicket<Float> GRAB_EXTENSION = DataTickets.create("asterion_minotaur_grab_extension", Float.class);
    private static final DataTicket<Integer> GRAB_ARM = DataTickets.create("asterion_minotaur_grab_arm", Integer.class);
    private static final DataTicket<Integer> HELD_PLAYER = DataTickets.create("asterion_minotaur_held_player", Integer.class);
    private static final DataTicket<Boolean> AXE_ACTION = DataTickets.create("asterion_minotaur_axe_action", Boolean.class);
    private static final DataTicket<Boolean> AUTHORED_POSE = DataTickets.create("asterion_minotaur_authored_pose", Boolean.class);
    private static final DataTicket<Float> IDLE_PHASE = DataTickets.create("asterion_minotaur_idle_phase", Float.class);
    private static final DataTicket<Float> IDLE_WEIGHT = DataTickets.create("asterion_minotaur_idle_weight", Float.class);
    private static final DataTicket<Float> HORN_WEIGHT = DataTickets.create("asterion_minotaur_horn_weight", Float.class);
    private static final DataTicket<Float> RAGE_WEIGHT = DataTickets.create("asterion_minotaur_rage_weight", Float.class);
    private static final DataTicket<Integer> ATTACK_TICKS = DataTickets.create("asterion_minotaur_attack_ticks", Integer.class);
    private static final DataTicket<Float> COLLAPSE = DataTickets.create("asterion_minotaur_collapse", Float.class);
    private static final DataTicket<Float> DOOR_ENTRY = DataTickets.create("asterion_minotaur_door_entry", Float.class);
    private final Map<UUID, LookPose> lookPoses = new HashMap<>();
    private final Map<UUID, GrabPose> grabPoses = new HashMap<>();

    public MinotaurGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new MinotaurGeoModel());
        withScale(1.0F);
        withRenderLayer(new MinotaurWeaponLayer(this));
        withRenderLayer(new MinotaurChainLayer(this));
        // The eye mask sits just outside the head surface, retaining depth occlusion by real geometry.
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "glow",
                // The current 512px atlas contains the glow bone's isolated eye islands.
                // The legacy 256px eye masks address a different UV layout and sample empty texels.
                Asterion.id("textures/entity/minotaur.png")) {

            @Override public boolean shouldRenderBone(EntityRenderState state) {
                return !state.isInvisible;
            }

            @Override protected boolean enhancedSurface(EntityRenderState state) { return true; }

            @Override protected net.minecraft.resources.Identifier amneticEmissionMesh(EntityRenderState state) {
                return getGeoModel().getModelResource(state);
            }

            @Override protected float emissiveStrength(EntityRenderState state) { return 1.35F; }

            @Override protected float surfaceBrightness(EntityRenderState state) {
                float collapse = state.getOrDefaultGeckolibData(COLLAPSE, 0F);
                if (collapse > 45 && collapse < 118) return .04F;
                return state.getOrDefaultGeckolibData(DOOR_ENTRY, -1F) >= 0 ? 1F : AsterionEmissiveConfig.minotaurEyeStrength();
            }

            @Override protected int emissiveColor(EntityRenderState state) {
                return state.getOrDefaultGeckolibData(EYE_TINT, 0xFFFFFFFF);
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
        MinotaurPoseBlend.capture(minotaur, state, partialTick);
        // Every viewer sees the server's target direction, not a different camera-facing torso.
        float bodyYaw = Mth.rotLerp(partialTick, minotaur.yBodyRotO, minotaur.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, minotaur.yHeadRotO, minotaur.yHeadRot);
        float targetYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -72F, 72F);
        float targetPitch = Mth.clamp(Mth.lerp(partialTick, minotaur.xRotO, minotaur.getXRot()), -34F, 42F);

        LookPose pose = lookPoses.computeIfAbsent(minotaur.getUUID(), ignored -> new LookPose());
        float frameTicks = Math.max(0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        float blend = 1.0F - (float)Math.pow(0.72D, frameTicks);
        pose.yaw += Mth.wrapDegrees(targetYaw - pose.yaw) * blend;
        pose.pitch += (targetPitch - pose.pitch) * blend;
        float desiredIdle = minotaur.animationState() == MinotaurEntity.AnimationState.IDLE
                && !minotaur.isPerformingReach() ? 1.0F : 0.0F;
        pose.idleWeight += (desiredIdle - pose.idleWeight)
                * (1.0F - (float)Math.pow(0.82D, frameTicks));
        state.addGeckolibData(LOOK_YAW, pose.yaw * Mth.DEG_TO_RAD);
        state.addGeckolibData(LOOK_PITCH, pose.pitch * Mth.DEG_TO_RAD);
        state.addGeckolibData(IDLE_PHASE, (minotaur.tickCount + partialTick) * 0.055F);
        state.addGeckolibData(IDLE_WEIGHT, pose.idleWeight);
        // Procedural rage/head/torso offsets snapped on as soon as an action became locomotion,
        // bypassing the controller's crossfade. Keep authored poses authoritative except the grab IK.
        state.addGeckolibData(AUTHORED_POSE, !minotaur.isPerformingGrab());
        state.addGeckolibData(HORN_WEIGHT, minotaur.isSpineCharging() ? 1.0F : 0.0F);
        state.addGeckolibData(RAGE_WEIGHT, minotaur.rage() / 12.0F);
        state.addGeckolibData(ATTACK_TICKS, minotaur.bossAttackAnimationTicks());
        state.addGeckolibData(COLLAPSE, minotaur.collapseAnimationTicks() > 0 ? minotaur.collapseAnimationTicks() + partialTick : 0F);
        state.addGeckolibData(DOOR_ENTRY, minotaur.doorEntryTicks() > 0 ? minotaur.doorEntryTicks() - 1 + partialTick : -1F);
        if (minotaur.isGreekFireLaserActive())
            state.addGeckolibData(DataTickets.RENDER_COLOR, 0x8856FF74);

        GrabPose grab = grabPoses.computeIfAbsent(minotaur.getUUID(), ignored -> new GrabPose());
        int grabTicks = minotaur.grabAttackTicks();
        float desiredGrab = 0.0F;
        float grabYaw = 0.0F;
        float grabPitch = 0.0F;
        float grabExtension = 0.65F;
        int liveArm = minotaur.reachArmSide();
        if (liveArm != 0) grab.arm = liveArm;
        if (minotaur.isPerformingReach() && !minotaur.isChainGrappleActive() && liveArm != 0) {
            desiredGrab = minotaur.isPerformingGrab()
                    ? grabTicks < 11 ? smoother(grabTicks / 11.0F)
                    : grabTicks < 49 ? 1.0F : 1.0F - smoother((grabTicks - 49) / 12.0F)
                    : grabTicks < 12 ? smoother(grabTicks / 12.0F)
                    : grabTicks < 43 ? 1.0F : 1.0F - smoother((grabTicks - 43) / 6.0F);
            Entity targetEntity = minotaur.level().getEntity(minotaur.grabTargetEntityId());
            if (targetEntity != null) {
                Vec3 shoulderCenter = minotaur.position().add(0.0D, minotaur.getBbHeight() * 0.68D, 0.0D);
                Vec3 targetCenter = targetEntity.position().add(0.0D, targetEntity.getBbHeight() * 0.52D, 0.0D);
                Vec3 delta = targetCenter.subtract(shoulderCenter);
                double horizontal = Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
                float worldYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
                grabYaw = Mth.clamp(Mth.wrapDegrees(worldYaw - bodyYaw), -78.0F, 78.0F) * Mth.DEG_TO_RAD;
                grabPitch = Mth.clamp((float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG),
                        -62.0F, 48.0F) * Mth.DEG_TO_RAD;
                grabExtension = Mth.clamp((float)(delta.length() / (minotaur.getBbHeight() * 0.72D)),
                        0.42F, 1.0F);
            }
        }
        float grabBlend = 1.0F - (float)Math.pow(0.38D, frameTicks);
        grab.weight += (desiredGrab - grab.weight) * grabBlend;
        grab.yaw += (grabYaw - grab.yaw) * grabBlend;
        grab.pitch += (grabPitch - grab.pitch) * grabBlend;
        grab.extension += (grabExtension - grab.extension) * grabBlend;
        state.addGeckolibData(GRAB_WEIGHT, grab.weight);
        state.addGeckolibData(GRAB_YAW, grab.yaw);
        state.addGeckolibData(GRAB_PITCH, grab.pitch);
        state.addGeckolibData(GRAB_EXTENSION, grab.extension);
        state.addGeckolibData(GRAB_ARM, grab.arm);
        state.addGeckolibData(HELD_PLAYER, minotaur.heldPlayerId());
        state.addGeckolibData(AXE_ACTION, minotaur.isAxeAttackActive());
        float rage = minotaur.rage() / 12.0F;
        if (minotaur.doorEntryTicks() > 0) {
            state.addGeckolibData(EYE_TINT, 0xFFD8FFFF);
        } else if (rage > 0.001F) {
            // Calm cyan hardens through furnace-orange into a saturated, fully enraged red.
            float pulse = 0.88F + 0.12F * Mth.sin((minotaur.tickCount + partialTick)
                    * (0.18F + rage * 0.38F));
            int red = 255;
            int green = Mth.floor(Mth.lerp(rage, 190.0F, 18.0F) * pulse);
            int blue = Mth.floor(Mth.lerp(rage, 92.0F, 6.0F) * pulse);
            int alpha = Mth.floor(Mth.lerp(rage, 185.0F, 255.0F));
            state.addGeckolibData(EYE_TINT, alpha << 24 | red << 16 | green << 8 | blue);
        } else if (minotaur.isExtremeBoss()) {
            float damage = minotaur.bossDamageFraction();
            int red = Mth.floor(Mth.lerp(damage, 205.0F, 255.0F));
            state.addGeckolibData(EYE_TINT, 0xFF000000 | red << 16 | 0xFFFF);
        } else state.addGeckolibData(EYE_TINT, 0xFFD8FFFF);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        int held = pass.getOrDefaultGeckolibData(HELD_PLAYER, -1);
        if (held >= 0) pass.addLocatorPositionListener(
                pass.getOrDefaultGeckolibData(GRAB_ARM, 1) >= 0 ? "right_player_grip" : "left_player_grip",
                (world, model, local) -> MinotaurHandAttachment.capture(held, world));
        // Let the action clips own their full pose; camera tracking and old charge/grab offsets would distort them.
        if (pass.getOrDefaultGeckolibData(AUTHORED_POSE, false)) {
            MinotaurPoseBlend.apply(pass, bones);
            return;
        }
        float yaw = pass.getOrDefaultGeckolibData(LOOK_YAW, 0.0F);
        float pitch = pass.getOrDefaultGeckolibData(LOOK_PITCH, 0.0F);
        // Camera-facing torso offsets would change the weapon's attack plane for every viewer.
        if (!pass.getOrDefaultGeckolibData(AXE_ACTION, false))
            rotateBone(bones, "body", -yaw * 0.18F, -pitch * 0.14F);
        rotateBone(bones, "neck", -yaw * 0.32F, -pitch * 0.31F);
        rotateBone(bones, "head", -yaw * 0.50F, -pitch * 0.55F);

        float idle = pass.getOrDefaultGeckolibData(IDLE_WEIGHT, 0.0F);
        if (idle > 0.001F) {
            float phase = pass.getOrDefaultGeckolibData(IDLE_PHASE, 0.0F);
            float breath = Mth.sin(phase) * idle;
            float shift = Mth.sin(phase * 0.47F + 1.1F) * idle;
            rotateBone3(bones, "body", breath * 0.025F, shift * 0.032F, shift * 0.018F);
            rotateBone3(bones, "neck", -breath * 0.018F, -shift * 0.020F, 0.0F);
            rotateBone3(bones, "head", breath * 0.012F, shift * 0.026F, -shift * 0.009F);
            rotateBone3(bones, "leftshoulder", -breath * 0.018F, 0.0F, shift * 0.012F);
            rotateBone3(bones, "rightshoulder", -breath * 0.018F, 0.0F, -shift * 0.012F);
        }

        float rage = pass.getOrDefaultGeckolibData(RAGE_WEIGHT, 0.0F);
        if (rage > 0.001F) {
            float phase = pass.getOrDefaultGeckolibData(IDLE_PHASE, 0.0F);
            float pulse = Mth.sin(phase * (2.2F + rage * 1.8F));
            rotateBone3(bones, "body", -0.035F * rage + pulse * 0.012F * rage,
                    pulse * 0.018F * rage, 0.0F);
            rotateBone3(bones, "leftshoulder", -0.10F * rage, 0.0F,
                    -0.055F * rage + pulse * 0.018F * rage);
            rotateBone3(bones, "rightshoulder", -0.10F * rage, 0.0F,
                    0.055F * rage - pulse * 0.018F * rage);
            rotateBone3(bones, "head", 0.025F * rage, pulse * 0.012F * rage, 0.0F);
        }

        float grab = pass.getOrDefaultGeckolibData(GRAB_WEIGHT, 0.0F);
        if (grab > 0.001F) {
            float targetYaw = pass.getOrDefaultGeckolibData(GRAB_YAW, 0.0F);
            float targetPitch = pass.getOrDefaultGeckolibData(GRAB_PITCH, 0.0F);
            float extension = pass.getOrDefaultGeckolibData(GRAB_EXTENSION, 0.65F);
            int arm = pass.getOrDefaultGeckolibData(GRAB_ARM, 1);
            applyArmReach(bones, arm, targetYaw, targetPitch, extension, grab);
            rotateBone3(bones, "body", targetPitch * 0.12F * grab,
                    -targetYaw * 0.18F * grab, 0.0F);
        }

        float horn = pass.getOrDefaultGeckolibData(HORN_WEIGHT, 0.0F);
        if (horn > 0.001F) {
            int ticks = pass.getOrDefaultGeckolibData(ATTACK_TICKS, 0);
            float lowered = ticks <= 28 ? smoother(ticks / 28.0F) : 1.0F;
            float runBob = ticks > 28 ? Mth.sin((ticks - 28) * 0.52F) * 0.035F : 0.0F;
            rotateBone3(bones, "lowerbody", -(0.08F + lowered * 0.13F - runBob * 0.4F) * horn,
                    0.0F, 0.0F);
            rotateBone3(bones, "body", -(0.18F + lowered * 0.27F + runBob) * horn, 0.0F, 0.0F);
            rotateBone3(bones, "neck", -(0.25F + lowered * 0.36F - runBob) * horn, 0.0F, 0.0F);
            rotateBone3(bones, "head", -(0.24F + lowered * 0.43F) * horn, 0.0F, 0.0F);
            translateBone(bones, "neck", 0.0F, -0.35F * lowered * horn, -0.85F * lowered * horn);
            translateBone(bones, "head", 0.0F, -0.55F * lowered * horn, -1.65F * lowered * horn);
            rotateBone3(bones, "lefthorn", -0.08F * lowered * horn, 0.0F, -0.06F * horn);
            rotateBone3(bones, "righthorn", -0.08F * lowered * horn, 0.0F, 0.06F * horn);
        }
        MinotaurPoseBlend.apply(pass, bones);
    }

    private static void applyArmReach(BoneSnapshots bones, int side, float yaw, float pitch,
                                      float extension, float weight) {
        boolean right = side >= 0;
        float sign = right ? 1.0F : -1.0F;
        String shoulder = right ? "rightshoulder" : "leftshoulder";
        String upperArm = right ? "rightarm" : "leftarm";
        String lowerArm = right ? "lowerrightarm" : "lowerleftarm";
        String hand = right ? "righthand" : "lefthand";
        float elbowBend = Mth.lerp(extension, 0.78F, 0.16F);

        rotateBone3(bones, shoulder, pitch * 0.22F * weight,
                -yaw * 0.54F * weight, sign * 0.13F * weight);
        rotateBone3(bones, upperArm, (-0.72F + pitch * 0.72F) * weight,
                (sign * 0.20F - yaw * 0.58F) * weight, sign * 0.68F * weight);
        rotateBone3(bones, lowerArm, -elbowBend * weight,
                (sign * 0.13F - yaw * 0.24F) * weight,
                -sign * (0.28F + (1.0F - extension) * 0.18F) * weight);
        rotateBone3(bones, hand, (-0.28F + pitch * 0.26F) * weight,
                (sign * 0.38F - yaw * 0.18F) * weight, -sign * 0.28F * weight);
    }

    private static void rotateBone(BoneSnapshots bones, String name, float yaw, float pitch) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(
                snapshot.getRotX() + pitch, snapshot.getRotY() + yaw, snapshot.getRotZ()));
    }

    private static void rotateBone3(BoneSnapshots bones, String name, float pitch, float yaw, float roll) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(snapshot.getRotX() + pitch,
                snapshot.getRotY() + yaw, snapshot.getRotZ() + roll));
    }

    private static void translateBone(BoneSnapshots bones, String name, float x, float y, float z) {
        bones.ifPresent(name, snapshot -> snapshot.setTranslation(snapshot.getTranslateX() + x,
                snapshot.getTranslateY() + y, snapshot.getTranslateZ() + z));
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static final class LookPose {
        private float yaw;
        private float pitch;
        private float idleWeight;
    }

    private static final class GrabPose {
        private float weight;
        private float yaw;
        private float pitch;
        private float extension = 0.65F;
        private int arm = 1;
    }

}

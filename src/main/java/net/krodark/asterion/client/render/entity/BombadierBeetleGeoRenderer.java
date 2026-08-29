package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BombadierBeetleGeoRenderer extends GeoEntityRenderer<BombadierBeetleEntity, EntityRenderState> {
    private static final DataTicket<Float> SURFACE_PITCH = DataTickets.create(
            "asterion_beetle_surface_pitch", Float.class);
    private static final DataTicket<Float> SURFACE_ROLL = DataTickets.create(
            "asterion_beetle_surface_roll", Float.class);
    private static final DataTicket<Float> SPRAY_PHASE = DataTickets.create(
            "asterion_beetle_spray_phase", Float.class);
    private static final DataTicket<Float> SPRAY_WEIGHT = DataTickets.create(
            "asterion_beetle_spray_weight", Float.class);
    private final Map<UUID, SurfacePose> surfacePoses = new HashMap<>();

    public BombadierBeetleGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new BombadierBeetleGeoModel());
        this.shadowRadius = 0.35F;
    }

    @Override
    public void addRenderData(BombadierBeetleEntity beetle, Void relatedObject,
                              EntityRenderState state, float partialTick) {
        Direction surface = beetle.attachedSurface();
        float targetPitch = surface == Direction.UP ? Mth.PI : 0.0F;
        float targetRoll = 0.0F;
        if (surface.getAxis().isHorizontal()) {
            // Convert the world-space wall normal into the beetle's yaw-relative frame. The old
            // cardinal mapping failed whenever the beetle turned around on the same wall.
            float yaw = beetle.getYRot();
            var forward = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, yaw);
            var localRight = new net.minecraft.world.phys.Vec3(-forward.z, 0.0D, forward.x);
            double wallOnRight = surface.getUnitVec3().dot(localRight);
            targetRoll = wallOnRight >= 0.0D ? Mth.HALF_PI : -Mth.HALF_PI;
            var motion = beetle.getDeltaMovement();
            double horizontalMotion = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (motion.lengthSqr() > 0.002D)
                targetPitch = Mth.clamp((float)Math.atan2(motion.y, horizontalMotion),
                        -1.48F, 1.48F);
        }
        SurfacePose pose = surfacePoses.computeIfAbsent(beetle.getUUID(), ignored -> new SurfacePose());
        float frameTicks = Math.max(0.05F,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        float pitchBlend = 1.0F - (float)Math.pow(0.80D, frameTicks);
        float rollBlend = 1.0F - (float)Math.pow(0.84D, frameTicks);
        pose.pitch += wrapRadians(targetPitch - pose.pitch) * pitchBlend;
        pose.roll += wrapRadians(targetRoll - pose.roll) * rollBlend;
        float desiredSprayWeight = beetle.defenceState() == BombadierBeetleEntity.DefenceState.FLEEING
                ? 1.0F : 0.0F;
        float sprayBlend = 1.0F - (float)Math.pow(0.76D, frameTicks);
        pose.sprayWeight += (desiredSprayWeight - pose.sprayWeight) * sprayBlend;
        state.addGeckolibData(SURFACE_PITCH, pose.pitch);
        state.addGeckolibData(SURFACE_ROLL, pose.roll);
        state.addGeckolibData(SPRAY_PHASE, (beetle.tickCount + partialTick) * 0.34F);
        state.addGeckolibData(SPRAY_WEIGHT, pose.sprayWeight);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        float pitch = pass.getOrDefaultGeckolibData(SURFACE_PITCH, 0.0F);
        float roll = pass.getOrDefaultGeckolibData(SURFACE_ROLL, 0.0F);
        bones.ifPresent("full", snapshot -> snapshot.setRotation(
                snapshot.getRotX() + pitch, snapshot.getRotY(), snapshot.getRotZ() + roll));

        float sprayWeight = pass.getOrDefaultGeckolibData(SPRAY_WEIGHT, 0.0F);
        if (sprayWeight > 0.001F) {
            float phase = pass.getOrDefaultGeckolibData(SPRAY_PHASE, 0.0F);
            float lift = Mth.sin(phase) * 0.12F * sprayWeight;
            float tilt = Mth.sin(phase + Mth.HALF_PI) * 0.026F * sprayWeight;
            bones.ifPresent("shell", snapshot -> {
                snapshot.setTranslation(snapshot.getTranslateX(),
                        snapshot.getTranslateY() + lift, snapshot.getTranslateZ());
                snapshot.setRotation(snapshot.getRotX() + tilt,
                        snapshot.getRotY(), snapshot.getRotZ());
            });
        }
    }

    private static final class SurfacePose {
        private float pitch;
        private float roll;
        private float sprayWeight;
    }

    private static float wrapRadians(float angle) {
        return (float)Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}

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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class BombadierBeetleGeoRenderer extends GeoEntityRenderer<BombadierBeetleEntity, EntityRenderState> {
    private static final DataTicket<Float> SURFACE_ROT_X = DataTickets.create(
            "asterion_beetle_surface_rotation_x", Float.class);
    private static final DataTicket<Float> SURFACE_ROT_Y = DataTickets.create(
            "asterion_beetle_surface_rotation_y", Float.class);
    private static final DataTicket<Float> SURFACE_ROT_Z = DataTickets.create(
            "asterion_beetle_surface_rotation_z", Float.class);
    private static final DataTicket<Float> SURFACE_ROT_W = DataTickets.create(
            "asterion_beetle_surface_rotation_w", Float.class);
    private static final DataTicket<Float> SPRAY_PHASE = DataTickets.create(
            "asterion_beetle_spray_phase", Float.class);
    private static final DataTicket<Float> SPRAY_WEIGHT = DataTickets.create(
            "asterion_beetle_spray_weight", Float.class);
    private final Map<UUID, SurfacePose> surfacePoses = new WeakHashMap<>();

    public BombadierBeetleGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new BombadierBeetleGeoModel());
        this.shadowRadius = 0.35F;
    }

    @Override
    public void addRenderData(BombadierBeetleEntity beetle, Void relatedObject,
                              EntityRenderState state, float partialTick) {
        Quaternionf targetRotation = calculateSurfaceRotation(beetle, partialTick);
        SurfacePose pose = surfacePoses.computeIfAbsent(beetle.getUUID(), ignored -> new SurfacePose());
        float frameTicks = Math.max(0.05F,
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        float orientationBlend = 1.0F - (float)Math.pow(0.82D, frameTicks);
        pose.orientation.slerp(targetRotation, orientationBlend).normalize();
        float desiredSprayWeight = beetle.defenceState() == BombadierBeetleEntity.DefenceState.FLEEING
                ? 1.0F : 0.0F;
        float sprayBlend = 1.0F - (float)Math.pow(0.76D, frameTicks);
        pose.sprayWeight += (desiredSprayWeight - pose.sprayWeight) * sprayBlend;
        state.addGeckolibData(SURFACE_ROT_X, pose.orientation.x);
        state.addGeckolibData(SURFACE_ROT_Y, pose.orientation.y);
        state.addGeckolibData(SURFACE_ROT_Z, pose.orientation.z);
        state.addGeckolibData(SURFACE_ROT_W, pose.orientation.w);
        state.addGeckolibData(SPRAY_PHASE, (beetle.tickCount + partialTick) * 0.34F);
        state.addGeckolibData(SPRAY_WEIGHT, pose.sprayWeight);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<EntityRenderState> pass) {
        super.adjustRenderPose(pass);

        Quaternionf surfaceRotation = new Quaternionf(
                pass.getOrDefaultGeckolibData(SURFACE_ROT_X, 0.0F),
                pass.getOrDefaultGeckolibData(SURFACE_ROT_Y, 0.0F),
                pass.getOrDefaultGeckolibData(SURFACE_ROT_Z, 0.0F),
                pass.getOrDefaultGeckolibData(SURFACE_ROT_W, 1.0F));
        pass.poseStack().mulPose(surfaceRotation.normalize());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
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

    /**
     * Builds a complete wall-local frame instead of guessing two Euler angles from a wall side.
     * Model +Y is kept opposite the attachment normal (the stomach faces the surface), while
     * model -Z follows the beetle's velocity projected onto that surface.
     */
    private Quaternionf calculateSurfaceRotation(BombadierBeetleEntity beetle, float partialTick) {
        Direction surface = beetle.attachedSurface();
        if (surface == Direction.DOWN)
            return new Quaternionf();

        Vec3 attachmentNormal = surface.getUnitVec3();
        Vec3 surfaceUp = attachmentNormal.scale(-1.0D);
        Vec3 motion = beetle.getDeltaMovement();
        Vec3 surfaceForward = motion.subtract(attachmentNormal.scale(motion.dot(attachmentNormal)));

        // Match the exact yaw transform applied by GeoEntityRenderer before adding our local pose.
        float renderYaw = calculateYRot(beetle, 0.0F, partialTick);
        Quaternionf baseYaw = new Quaternionf().rotationY((180.0F - renderYaw) * Mth.DEG_TO_RAD);
        if (surfaceForward.lengthSqr() < 1.0E-5D) {
            Vector3f fallback = baseYaw.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            surfaceForward = new Vec3(fallback.x, fallback.y, fallback.z)
                    .subtract(attachmentNormal.scale(
                            fallback.x * attachmentNormal.x
                                    + fallback.y * attachmentNormal.y
                                    + fallback.z * attachmentNormal.z));
        }
        if (surfaceForward.lengthSqr() < 1.0E-5D)
            surfaceForward = Math.abs(surfaceUp.y) < 0.9D
                    ? surfaceUp.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    : surfaceUp.cross(new Vec3(1.0D, 0.0D, 0.0D));
        surfaceForward = surfaceForward.normalize();

        Quaternionf inverseBaseYaw = new Quaternionf(baseYaw).conjugate();
        Vector3f localUp = inverseBaseYaw.transform(new Vector3f(
                (float)surfaceUp.x, (float)surfaceUp.y, (float)surfaceUp.z)).normalize();
        Vector3f localForward = inverseBaseYaw.transform(new Vector3f(
                (float)surfaceForward.x, (float)surfaceForward.y, (float)surfaceForward.z));
        // Re-project after float conversion so the frame remains exactly orthogonal.
        localForward.sub(new Vector3f(localUp).mul(localForward.dot(localUp))).normalize();

        Quaternionf alignUp = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), localUp);
        Vector3f alignedForward = alignUp.transform(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
        float forwardDot = Mth.clamp(alignedForward.dot(localForward), -1.0F, 1.0F);
        float signedTurn = (float)Math.atan2(
                localUp.dot(new Vector3f(alignedForward).cross(localForward)), forwardDot);
        Quaternionf faceAlongSurface = new Quaternionf().rotationAxis(signedTurn, localUp);

        return faceAlongSurface.mul(alignUp).normalize();
    }

    private static final class SurfacePose {
        private final Quaternionf orientation = new Quaternionf();
        private float sprayWeight;
    }
}

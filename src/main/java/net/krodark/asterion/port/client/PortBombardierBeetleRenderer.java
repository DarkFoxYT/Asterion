package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Restores smooth wall/ceiling alignment and the defensive shell motion. */
public final class PortBombardierBeetleRenderer extends SimpleGeoEntityRenderer<BombadierBeetleEntity> {
    private final Map<UUID, Quaternionf> surfaceRotations = new HashMap<>();
    private float sprayPhase;
    private float sprayWeight;

    public PortBombardierBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/bombadier_beetle"),
                Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.35F, 1.0F);
    }

    @Override

//? if >=1.20.5 {
protected void applyRotations(BombadierBeetleEntity beetle, PoseStack poses, float age,
                                  float yaw, float partialTick, float nativeScale) {
//?} else {
/*protected void applyRotations(BombadierBeetleEntity beetle, PoseStack poses, float age,
                                  float yaw, float partialTick) {
 float nativeScale = 1;*/
//?}


//? if >=1.20.5 {
super.applyRotations(beetle, poses, age, yaw, partialTick, nativeScale);
//?} else {
/*super.applyRotations(beetle, poses, age, yaw, partialTick);*/
//?}

        Quaternionf target = calculateSurfaceRotation(beetle, yaw);
        Quaternionf smoothed = surfaceRotations.computeIfAbsent(beetle.getUUID(), ignored -> new Quaternionf());
        smoothed.slerp(target, 0.22F).normalize();
        poses.mulPose(smoothed);
        sprayPhase = (beetle.tickCount + partialTick) * 0.34F;
        float targetWeight = beetle.defenceState() == BombadierBeetleEntity.DefenceState.FLEEING ? 1.0F : 0.0F;
        sprayWeight += (targetWeight - sprayWeight) * 0.24F;
    }

    @Override

//? if >=1.20.5 {
public void renderRecursively(PoseStack poses, BombadierBeetleEntity beetle, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean rerender, float partialTick, int light, int overlay, int colour) {
//?} else {
/*public void renderRecursively(PoseStack poses, BombadierBeetleEntity beetle, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean rerender, float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int colour = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

        if (bone.getName().equals("shell") && sprayWeight > 0.001F) {
            bone.setPosY(bone.getInitialSnapshot().getOffsetY()
                    + Mth.sin(sprayPhase) * 0.12F * sprayWeight);
            bone.setRotX(bone.getInitialSnapshot().getRotX()
                    + Mth.sin(sprayPhase + Mth.HALF_PI) * 0.026F * sprayWeight);
        }

//? if >=1.20.5 {
super.renderRecursively(poses, beetle, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
//?} else {
/*super.renderRecursively(poses, beetle, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, ((colour >> 16) & 255) / 255F, ((colour >> 8) & 255) / 255F, (colour & 255) / 255F, ((colour >>> 24) & 255) / 255F);*/
//?}

    }

    private static Quaternionf calculateSurfaceRotation(BombadierBeetleEntity beetle, float renderYaw) {
        Direction surface = beetle.attachedSurface();
        if (surface == Direction.DOWN) return new Quaternionf();
        Vec3 normal = Vec3.atLowerCornerOf(surface.getNormal());
        Vec3 up = normal.scale(-1.0D);
        Vec3 motion = beetle.getDeltaMovement();
        Vec3 forward = motion.subtract(normal.scale(motion.dot(normal)));
        Quaternionf baseYaw = new Quaternionf().rotationY((180.0F - renderYaw) * Mth.DEG_TO_RAD);
        if (forward.lengthSqr() < 1.0E-5D) {
            Vector3f fallback = baseYaw.transform(new Vector3f(0, 0, -1));
            forward = new Vec3(fallback.x, fallback.y, fallback.z)
                    .subtract(normal.scale(fallback.x * normal.x + fallback.y * normal.y + fallback.z * normal.z));
        }
        if (forward.lengthSqr() < 1.0E-5D)
            forward = Math.abs(up.y) < 0.9D ? up.cross(new Vec3(0, 1, 0)) : up.cross(new Vec3(1, 0, 0));
        forward = forward.normalize();
        Quaternionf inverseYaw = new Quaternionf(baseYaw).conjugate();
        Vector3f localUp = inverseYaw.transform(up.toVector3f()).normalize();
        Vector3f localForward = inverseYaw.transform(forward.toVector3f());
        localForward.sub(new Vector3f(localUp).mul(localForward.dot(localUp))).normalize();
        Quaternionf alignUp = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), localUp);
        Vector3f alignedForward = alignUp.transform(new Vector3f(0, 0, -1)).normalize();
        float turn = (float)Math.atan2(localUp.dot(new Vector3f(alignedForward).cross(localForward)),
                Mth.clamp(alignedForward.dot(localForward), -1.0F, 1.0F));
        return new Quaternionf().rotationAxis(turn, localUp).mul(alignUp).normalize();
    }
}

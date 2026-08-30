package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.base.BoneSnapshots;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Model-independent spring chain for the centipede body.
 *
 * <p>Model contract for the incoming final asset:
 * <ul>
 *   <li>{@code head}: ride-bearing front piece.</li>
 *   <li>{@code segment_0} through {@code segment_31}: ordered front-to-back joints.</li>
 *   <li>{@code chain_end} (preferred) or {@code tail}: terminal piece.</li>
 * </ul>
 * Segment bones may be added to the model without Java changes. The entity's synced segment
 * count decides how many are visible, and this solver automatically propagates turning,
 * surface changes, and locomotion lag through every available joint.
 */
public final class ProceduralCentipedeChain {
    public static final int MAX_SEGMENTS = 32;
    private static final float SPRING = 0.19F;
    private static final float DAMPING = 0.70F;

    private final float[] yaw = new float[MAX_SEGMENTS];
    private final float[] yawVelocity = new float[MAX_SEGMENTS];
    private final float[] pitch = new float[MAX_SEGMENTS];
    private final float[] pitchVelocity = new float[MAX_SEGMENTS];
    private Vec3 previousForward;
    private Vec3 previousNormal;

    public Pose update(ScarletCentipedeEntity entity, float partialTick, float frameTicks) {
        Vec3 normal = entity.attachmentNormal().normalize();
        Vec3 up = normal.scale(-1.0D);
        Vec3 forward = entity.surfaceForward();
        forward = forward.subtract(normal.scale(forward.dot(normal)));
        if (forward.lengthSqr() < 1.0E-6D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();

        if (previousForward == null || previousNormal == null) {
            previousForward = forward;
            previousNormal = normal;
        }

        float turn = signedAngle(previousForward, forward, up);
        Vec3 right = forward.cross(up).normalize();
        float surfacePitch = signedAngle(previousNormal.scale(-1.0D), up, right);
        float speed = (float)Mth.clamp(entity.getDeltaMovement().length() / 0.31D, 0.0D, 1.0D);
        float phase = (entity.tickCount + partialTick) * 0.58F;
        float dt = Mth.clamp(frameTicks, 0.05F, 2.0F);
        float propagatedYaw = Mth.clamp(turn, -0.75F, 0.75F) * 0.82F;
        float propagatedPitch = Mth.clamp(surfacePitch, -0.75F, 0.75F) * 0.68F;

        for (int index = 0; index < MAX_SEGMENTS; index++) {
            float lag = (float)Math.pow(0.84D, index);
            float gait = Mth.sin(phase - index * 0.72F) * 0.045F * speed;
            float targetYaw = propagatedYaw * lag + gait;
            float targetPitch = propagatedPitch * lag
                    + Mth.cos(phase * 1.15F - index * 0.58F) * 0.018F * speed;
            yawVelocity[index] += (targetYaw - yaw[index]) * SPRING * dt;
            pitchVelocity[index] += (targetPitch - pitch[index]) * SPRING * dt;
            float damping = (float)Math.pow(DAMPING, dt);
            yawVelocity[index] *= damping;
            pitchVelocity[index] *= damping;
            yaw[index] += yawVelocity[index] * dt;
            pitch[index] += pitchVelocity[index] * dt;
            propagatedYaw = yaw[index] * 0.88F;
            propagatedPitch = pitch[index] * 0.88F;
        }

        previousForward = forward;
        previousNormal = normal;
        return new Pose(entity.chainSegmentCount(), Arrays.copyOf(yaw, yaw.length),
                Arrays.copyOf(pitch, pitch.length), -turn * 0.16F);
    }

    private static float signedAngle(Vec3 from, Vec3 to, Vec3 axis) {
        Vec3 a = from.normalize();
        Vec3 b = to.normalize();
        double dot = Mth.clamp(a.dot(b), -1.0D, 1.0D);
        return (float)Math.atan2(axis.dot(a.cross(b)), dot);
    }

    public record Pose(int activeSegments, float[] yaw, float[] pitch, float headBank) {
        public void apply(BoneSnapshots bones) {
            bones.ifPresent("head", snapshot -> snapshot.setRotZ(snapshot.getRotZ() + headBank));
            for (int index = 0; index < MAX_SEGMENTS; index++) {
                final int joint = index;
                bones.ifPresent("segment_" + index, snapshot -> {
                    boolean active = joint < activeSegments;
                    snapshot.skipRender(!active).skipChildrenRender(!active);
                    if (active) snapshot.setRotation(snapshot.getRotX() + pitch[joint],
                            snapshot.getRotY() + yaw[joint], snapshot.getRotZ());
                });
            }
            applyEnd(bones, "chain_end");
            applyEnd(bones, "tail");
        }

        private void applyEnd(BoneSnapshots bones, String name) {
            int joint = Mth.clamp(activeSegments - 1, 0, MAX_SEGMENTS - 1);
            bones.ifPresent(name, snapshot -> snapshot.setRotation(
                    snapshot.getRotX() + pitch[joint], snapshot.getRotY() + yaw[joint], snapshot.getRotZ()));
        }
    }
}

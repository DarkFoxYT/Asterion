package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.base.BoneSnapshots;
import net.krodark.asterion.entity.CentipedeChain;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Render-only adapter. The entity owns the world positions, contacts and rider poses. */
public final class ProceduralCentipedeChain {
    public static final int MAX_SEGMENTS = CentipedeChain.MAX_SEGMENTS;

    public static Pose extract(ScarletCentipedeEntity entity, float partialTick, Vec3 renderOrigin) {
        Vector3f[] positions = new Vector3f[entity.chainSegmentCount()];
        Vector3f[] rotations = new Vector3f[positions.length];
        float[] gait = new float[positions.length], speed = new float[positions.length];
        for (int i = 0; i < positions.length; i++) {
            CentipedeChain.Pose raw = entity.chainPose(i, partialTick);
            Vec3 renderDelta = entity.getPosition(partialTick).subtract(entity.position());
            CentipedeChain.Pose pose = new CentipedeChain.Pose(raw.position().add(renderDelta),
                    raw.normal(), raw.forward());
            positions[i] = CentipedeFrame.boneTranslation(pose.position().subtract(renderOrigin));
            rotations[i] = CentipedeFrame.boneAngles(CentipedeFrame.rotation(pose.normal(), pose.forward()));
            gait[i] = entity.segmentGait(i, partialTick);
            speed[i] = entity.segmentSpeed(i, partialTick);
        }
        return new Pose(positions, rotations, gait, speed);
    }

    public record Pose(Vector3f[] positions, Vector3f[] rotations, float[] gaitPhase, float[] speed) {
        public void apply(BoneSnapshots bones) {
            // Position and rotate the complete head rig about its body origin, not the neck pivot.
            applyAnchor(bones, "head_anchor", 0);
            for (int i = 0; i < MAX_SEGMENTS; i++) {
                applyAnchor(bones, "segment_anchor_" + i, i);
                if (i < positions.length) animateLegs(bones, i);
            }
        }

        private void applyAnchor(BoneSnapshots bones, String name, int index) {
            bones.ifPresent(name, snapshot -> {
                boolean visible = index < positions.length;
                snapshot.skipRender(!visible).skipChildrenRender(!visible);
                if (visible) {
                    Vector3f p = positions[index], r = rotations[index];
                    snapshot.setTranslation(p.x, p.y, p.z);
                    snapshot.setRotation(r.x, r.y, r.z);
                }
            });
        }

        private void animateLegs(BoneSnapshots bones, int index) {
            float wave = Mth.sin(gaitPhase[index] * 1.55F - index * 0.78F) * 0.42F * speed[index];
            String suffix = "_" + index;
            rotateLeg(bones, "leftfrontleg" + suffix, wave);
            rotateLeg(bones, "leftmidleg" + suffix, -wave);
            rotateLeg(bones, "leftbackleg" + suffix, wave);
            rotateLeg(bones, "rightfrontleg" + suffix, -wave);
            rotateLeg(bones, "rightmidleg" + suffix, wave);
            rotateLeg(bones, "rightbackleg" + suffix, -wave);
        }

        private static void rotateLeg(BoneSnapshots bones, String name, float amount) {
            bones.ifPresent(name, snapshot -> snapshot.setRotY(snapshot.getRotY() + amount));
        }
    }
}

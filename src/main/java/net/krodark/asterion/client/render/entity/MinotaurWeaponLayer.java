package net.krodark.asterion.client.render.entity;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.client.ragdoll.MinotaurAxeVisual;
import net.krodark.asterion.client.ragdoll.MinotaurSwordVisual;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** One layer owns all custom hand/hip/back weapons, preventing duplicate equipped copies. */
public final class MinotaurWeaponLayer extends GeoRenderLayer<MinotaurEntity, Void, EntityRenderState> {
    private static final DataTicket<String> AXE_BONE = DataTickets.create("asterion_axe_bone", String.class);
    private static final DataTicket<Boolean> DEFEATED = DataTickets.create("asterion_weapons_dropped", Boolean.class);
    private static final DataTicket<Integer> OWNER = DataTickets.create("asterion_weapon_owner", Integer.class);
    private static final DataTicket<Boolean> SWORDS_DRAWN = DataTickets.create("asterion_swords_drawn", Boolean.class);
    private static final DataTicket<Float> SWORD_TRAIL = DataTickets.create("asterion_sword_trail", Float.class);
    private static final RenderType TRAIL_MATERIAL = RenderTypes.lightning();
    private static final String[] SIDES = {"right", "left"};
    private final Map<Long, Trail> trails = new HashMap<>();

    public MinotaurWeaponLayer(MinotaurGeoRenderer renderer) { super(renderer); }

    @Override public void addRenderData(MinotaurEntity boss, Void ignored, EntityRenderState state, float partial) {
        int mode = boss.renderedWeaponMode();
        state.addGeckolibData(DEFEATED, boss.isDefeatedBoss());
        state.addGeckolibData(SWORDS_DRAWN, mode == 2);
        state.addGeckolibData(AXE_BONE, boss.axeInWorld() ? "" : mode == 1 ? "axe_grip" : "axe_back");
        state.addGeckolibData(OWNER, boss.getId());
        state.addGeckolibData(SWORD_TRAIL, boss.swordTrailStrength(partial));
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        if (!pass.willRender() || pass.renderState().isInvisible) return;
        if (pass.renderState().getOrDefaultGeckolibData(DEFEATED, false)) return;
        boolean drawn = pass.renderState().getOrDefaultGeckolibData(SWORDS_DRAWN, false);
        for (String side : SIDES)
            pass.model().getBone(drawn ? (side.equals("right") ? "hand_itemR" : "hand_itemL") : "lowerbody").ifPresent(bone ->
                    consumer.accept(bone, (posed, ignored, tasks) -> {
                        var poses = posed.poseStack();
                        poses.pushPose();
                        if (!drawn) {
                            int sign = side.equals("right") ? -1 : 1;
                            float age = (float)pass.renderState().getAnimatableAge();
                            float breathe = Mth.sin(age * 0.075F + (sign < 0 ? 0F : 0.65F));
                            float settle = Mth.sin(age * 0.16F + (sign < 0 ? 0F : Mth.PI));
                            // Keep the long tips clear of the floor and give the scabbards
                            // a tiny asynchronous breathing/stride sway instead of a frozen pose.
                            poses.translate(sign * 17.0 / 16,
                                    17.0 / 16 + breathe * 0.025F,
                                    3.0 / 16 + settle * 0.018F);
                            poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(168 + breathe * 1.25F));
                            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-sign * (6 + settle * 1.1F)));
                        }
                        if (drawn) renderSwordTrail(pass, posed, tasks, side, poses);
                        MinotaurSwordVisual.submit(
                                poses, tasks, posed.cameraState(), posed.packedLight());
                        poses.popPose();
                    }));
        String name = pass.renderState().getOrDefaultGeckolibData(AXE_BONE, "");
        if (name.isEmpty()) return;
        pass.model().getBone(name).or(() -> pass.model().getBone(name.equals("axe_grip") ? "hand_itemR" : "body"))
                .ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            var poses = posed.poseStack();
            poses.pushPose();
            if (bone.name().equals("body")) {
                // Centre the axe diagonally on the back, with the blade turned clear
                // of the torso's rear face (roughly Z=1.1 in this bone's space).
                poses.translate(0, .82, 1.42);
                poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
                poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                poses.translate(0, -MinotaurAxeEntity.CENTER_Y, 0);
            } else {
                if (name.equals("axe_back")) {
                    poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
                    poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                }
                poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);
            }
            if (name.equals("axe_grip")) MinotaurAxeVisual.captureHand(
                    posed.renderState().getGeckolibData(OWNER), poses, posed.cameraState());
            MinotaurAxeVisual.submit(poses, tasks, posed.cameraState(), posed.packedLight(), 0);
            poses.popPose();
        }));
    }

    private void renderSwordTrail(RenderPassInfo<EntityRenderState> pass,
                                  RenderPassInfo<EntityRenderState> posed,
                                  net.minecraft.client.renderer.SubmitNodeCollector tasks,
                                  String side, PoseStack poses) {
        int owner = pass.renderState().getOrDefaultGeckolibData(OWNER, -1);
        long key = ((long)owner << 1) ^ (side.equals("right") ? 1L : 0L);
        float strength = pass.renderState().getOrDefaultGeckolibData(SWORD_TRAIL, 0.0F);
        Trail trail = trails.computeIfAbsent(key, ignored -> new Trail());
        if (strength <= 0.005F) {
            trail.samples.clear();
            trail.lastStamp = Double.NEGATIVE_INFINITY;
            return;
        }

        double stamp = pass.renderState().getAnimatableAge();
        Vector3f rootVector = poses.last().pose().transformPosition(
                new Vector3f(0.0F, 19.0F / 16.0F, 0.0F));
        Vector3f tipVector = poses.last().pose().transformPosition(
                new Vector3f(0.0F, 84.0F / 16.0F, 0.0F));
        Vec3 camera = posed.cameraState().pos;
        Sample sample = new Sample(new Vec3(rootVector.x, rootVector.y, rootVector.z).add(camera),
                new Vec3(tipVector.x, tipVector.y, tipVector.z).add(camera));
        if (stamp > trail.lastStamp && (trail.samples.isEmpty()
                || trail.samples.getLast().tip.distanceToSqr(sample.tip) > 0.0004D)) {
            trail.samples.addLast(sample);
            trail.lastStamp = stamp;
            while (trail.samples.size() > 7) trail.samples.removeFirst();
        }
        if (trail.samples.size() < 2) return;
        List<Sample> snapshot = new ArrayList<>(trail.samples);
        tasks.submitCustomGeometry(new PoseStack(), TRAIL_MATERIAL,
                (pose, out) -> drawTrail(pose, out, snapshot, camera, strength));
    }

    private static void drawTrail(PoseStack.Pose pose, VertexConsumer out, List<Sample> samples,
                                  Vec3 camera, float strength) {
        int segments = samples.size() - 1;
        for (int index = 0; index < segments; index++) {
            Sample from = samples.get(index);
            Sample to = samples.get(index + 1);
            float age = (index + 1) / (float)segments;
            int alpha = Math.round(150.0F * strength * age * age);
            int red = 255;
            int green = Math.round(155 + 80 * age);
            int blue = Math.round(55 + 120 * age);
            vertex(out, pose, from.root.subtract(camera), red, green, blue, Math.round(alpha * .35F));
            vertex(out, pose, from.tip.subtract(camera), red, green, blue, alpha);
            vertex(out, pose, to.tip.subtract(camera), red, green, blue, alpha);
            vertex(out, pose, to.root.subtract(camera), red, green, blue, Math.round(alpha * .35F));
            vertex(out, pose, to.root.subtract(camera), red, green, blue, Math.round(alpha * .35F));
            vertex(out, pose, to.tip.subtract(camera), red, green, blue, alpha);
            vertex(out, pose, from.tip.subtract(camera), red, green, blue, alpha);
            vertex(out, pose, from.root.subtract(camera), red, green, blue, Math.round(alpha * .35F));
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point,
                               int red, int green, int blue, int alpha) {
        out.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
                .setColor(red, green, blue, alpha);
    }

    private record Sample(Vec3 root, Vec3 tip) { }
    private static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        double lastStamp = Double.NEGATIVE_INFINITY;
    }
}

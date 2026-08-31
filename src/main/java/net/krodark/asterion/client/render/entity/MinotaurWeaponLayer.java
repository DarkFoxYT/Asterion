package net.krodark.asterion.client.render.entity;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.krodark.asterion.client.ragdoll.MinotaurAxeVisual;
import net.krodark.asterion.client.ragdoll.MinotaurSwordVisual;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import java.util.function.BiConsumer;

/** One layer owns all custom hand/hip/back weapons, preventing duplicate equipped copies. */
public final class MinotaurWeaponLayer extends GeoRenderLayer<MinotaurEntity, Void, EntityRenderState> {
    private static final DataTicket<String> AXE_BONE = DataTickets.create("asterion_axe_bone", String.class);
    private static final DataTicket<Integer> OWNER = DataTickets.create("asterion_weapon_owner", Integer.class);
    private static final DataTicket<Boolean> SWORDS_DRAWN = DataTickets.create("asterion_swords_drawn", Boolean.class);
    private static final String[] SIDES = {"right", "left"};

    public MinotaurWeaponLayer(MinotaurGeoRenderer renderer) { super(renderer); }

    @Override public void addRenderData(MinotaurEntity boss, Void ignored, EntityRenderState state, float partial) {
        int mode = boss.renderedWeaponMode();
        state.addGeckolibData(SWORDS_DRAWN, mode == 2);
        state.addGeckolibData(AXE_BONE, boss.axeInWorld() ? "" : mode == 1 ? "axe_grip" : "axe_back");
        state.addGeckolibData(OWNER, boss.getId());
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        if (!pass.willRender() || pass.renderState().isInvisible) return;
        boolean drawn = pass.renderState().getOrDefaultGeckolibData(SWORDS_DRAWN, false);
        for (String side : SIDES)
            pass.model().getBone(drawn ? (side.equals("right") ? "hand_itemR" : "hand_itemL") : "lowerbody").ifPresent(bone ->
                    consumer.accept(bone, (posed, ignored, tasks) -> {
                        var poses = posed.poseStack();
                        poses.pushPose();
                        if (!drawn) {
                            int sign = side.equals("right") ? -1 : 1;
                            // Thin edge against the hip, blade trailing behind the thigh.
                            // The mesh is broad in Z, so a 90-degree yaw made it stick out sideways.
                            poses.translate(sign * 17.0 / 16, 10.0 / 16, 3.0 / 16);
                            poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(168));
                            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-sign * 6));
                        }
                        MinotaurSwordVisual.submit(poses, tasks, posed.cameraState(), posed.packedLight(), drawn);
                        poses.popPose();
                    }));
        String name = pass.renderState().getOrDefaultGeckolibData(AXE_BONE, "");
        if (name.isEmpty()) return;
        pass.model().getBone(name).or(() -> pass.model().getBone(name.equals("axe_grip") ? "hand_itemR" : "body"))
                .ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            var poses = posed.poseStack();
            poses.pushPose();
            if (bone.name().equals("body")) {
                poses.translate(0, -.2, 1.2);
                poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(30));
            }
            poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);
            if (name.equals("axe_grip")) MinotaurAxeVisual.captureHand(
                    posed.renderState().getGeckolibData(OWNER), poses, posed.cameraState());
            MinotaurAxeVisual.submit(poses, tasks, posed.cameraState(), posed.packedLight(), 0);
            poses.popPose();
        }));
    }
}

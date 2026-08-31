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
            pass.model().getBone(drawn ? (side.equals("right") ? "hand_itemR" : "hand_itemL") : "sword_hip_" + side).ifPresent(bone ->
                    consumer.accept(bone, (posed, ignored, tasks) -> MinotaurSwordVisual.submit(
                            posed.poseStack(), tasks, posed.cameraState(), posed.packedLight())));
        String name = pass.renderState().getOrDefaultGeckolibData(AXE_BONE, "");
        if (name.isEmpty()) return;
        pass.model().getBone(name).ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            var poses = posed.poseStack();
            poses.pushPose();
            poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);
            if (name.equals("axe_grip")) MinotaurAxeVisual.captureHand(
                    posed.renderState().getGeckolibData(OWNER), poses, posed.cameraState());
            MinotaurAxeVisual.submit(poses, tasks, posed.cameraState(), posed.packedLight(), 0);
            poses.popPose();
        }));
    }
}

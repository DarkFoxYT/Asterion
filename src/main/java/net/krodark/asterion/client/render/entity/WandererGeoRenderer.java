package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.entity.WandererEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WandererGeoRenderer extends GeoEntityRenderer<WandererEntity, EntityRenderState> {
    private static final DataTicket<Boolean> EYES_VISIBLE = DataTickets.create("asterion_wanderer_eyes_visible", Boolean.class);
    private static final DataTicket<Float> EYE_X = DataTickets.create("asterion_wanderer_eye_x", Float.class);
    private static final DataTicket<Float> EYE_Y = DataTickets.create("asterion_wanderer_eye_y", Float.class);
    public WandererGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new WandererGeoModel());
        withRenderLayer(eyeLayer("eyeleft"));
        withRenderLayer(eyeLayer("eyeright"));
        shadowRadius = 0.45F;
    }
    private AsterionEmissiveBoneLayer<WandererEntity, Void, EntityRenderState> eyeLayer(String bone) {
        return new AsterionEmissiveBoneLayer<>(this, bone, Asterion.id("textures/entity/wanderer.png")) {
            @Override protected boolean usesModelTextureCoordinates() { return true; }
            @Override protected float surfaceBrightness(EntityRenderState state) { return 1F; }
            @Override protected float emissiveStrength(EntityRenderState state) { return .9F; }
            @Override protected boolean enhancedSurface(EntityRenderState state) { return false; }
            @Override protected int emissiveColor(EntityRenderState state) { return 0xFFFFFFFF; }
            @Override protected boolean backfaceCulling(EntityRenderState state) { return false; }
            @Override protected net.minecraft.resources.Identifier amneticEmissionMesh(EntityRenderState state) {
                return getGeoModel().getModelResource(state);
            }
            @Override protected void renderBone(RenderPassInfo<EntityRenderState> pass, GeoBone bone,
                    net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                if (pass.getOrDefaultGeckolibData(EYES_VISIBLE, false)) super.renderBone(pass, bone, tasks);
            }
        };
    }
    @Override public void addRenderData(WandererEntity wanderer, Void related, EntityRenderState state, float partialTick) {
        state.addGeckolibData(EYES_VISIBLE, wanderer.state() != WandererEntity.State.DROWNING);
        int glancePhase = Math.floorMod(wanderer.tickCount + wanderer.getId() * 31, 120);
        float glance = (float)(.5D - .5D * Math.cos(glancePhase * Math.PI / 60D));
        boolean tracking = wanderer.state() != WandererEntity.State.DROWNING;
        Player player = tracking ? wanderer.level().getNearestPlayer(wanderer, 32D) : null;
        if (player == null) {
            state.addGeckolibData(EYE_X, 0F);
            state.addGeckolibData(EYE_Y, 0F);
            return;
        }
        float yaw = Mth.rotLerp(partialTick, wanderer.yHeadRotO, wanderer.yHeadRot) * Mth.DEG_TO_RAD;
        Vec3 toward = player.getEyePosition().subtract(wanderer.getEyePosition());
        double forward = toward.z * Math.cos(yaw) - toward.x * Math.sin(yaw);
        double right = toward.x * Math.cos(yaw) + toward.z * Math.sin(yaw);
        double depth = Math.max(1D, Math.abs(forward));
        // The eyes slide only over the mask's X/Y plane; the mask keeps their depth fixed.
        state.addGeckolibData(EYE_X, (float)Mth.clamp(right / depth * .8D, -.38D, .38D) * glance);
        state.addGeckolibData(EYE_Y, (float)Mth.clamp(toward.y / depth * .8D, -.32D, .32D) * glance);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        super.adjustModelBonesForRender(pass, bones);
        float x = pass.getOrDefaultGeckolibData(EYE_X, 0F), y = pass.getOrDefaultGeckolibData(EYE_Y, 0F);
        bones.ifPresent("eyeleft", bone -> bone.setTranslation(bone.getTranslateX() + x, bone.getTranslateY() + y, bone.getTranslateZ()));
        bones.ifPresent("eyeright", bone -> bone.setTranslation(bone.getTranslateX() + x, bone.getTranslateY() + y, bone.getTranslateZ()));
    }
}

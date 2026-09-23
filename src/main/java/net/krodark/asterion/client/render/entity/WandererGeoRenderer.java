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

public final class WandererGeoRenderer extends GeoEntityRenderer<WandererEntity, EntityRenderState> {
    private static final DataTicket<Boolean> WATCHING = DataTickets.create("asterion_wanderer_watching", Boolean.class);
    private static final DataTicket<Float> EYE_YAW = DataTickets.create("asterion_wanderer_eye_yaw", Float.class);
    private static final DataTicket<Float> EYE_PITCH = DataTickets.create("asterion_wanderer_eye_pitch", Float.class);
    public WandererGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new WandererGeoModel());
        withRenderLayer(eyeLayer("eyeleft"));
        withRenderLayer(eyeLayer("eyeright"));
        shadowRadius = 0.45F;
    }
    private AsterionEmissiveBoneLayer<WandererEntity, Void, EntityRenderState> eyeLayer(String bone) {
        return new AsterionEmissiveBoneLayer<>(this, bone, Asterion.id("textures/entity/wanderer.png")) {
            @Override protected float surfaceBrightness(EntityRenderState state) { return 1.0F; }
            @Override protected float emissiveStrength(EntityRenderState state) { return 4.0F; }
            @Override protected boolean enhancedSurface(EntityRenderState state) { return true; }
            @Override protected net.minecraft.resources.Identifier amneticEmissionMesh(EntityRenderState state) {
                return getGeoModel().getModelResource(state);
            }
            @Override protected void renderBone(RenderPassInfo<EntityRenderState> pass, GeoBone bone,
                    net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                if (pass.getOrDefaultGeckolibData(WATCHING, false)) super.renderBone(pass, bone, tasks);
            }
        };
    }
    @Override public void addRenderData(WandererEntity wanderer, Void related, EntityRenderState state, float partialTick) {
        state.addGeckolibData(WATCHING, wanderer.isWatching());
        // Each pair takes a long, irregular glance, then goes still again in the fog.
        boolean tracking = wanderer.isWatching()
                && Math.floorMod(wanderer.tickCount + wanderer.getId() * 31, 120) < 72;
        float bodyYaw = Mth.rotLerp(partialTick, wanderer.yBodyRotO, wanderer.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, wanderer.yHeadRotO, wanderer.yHeadRot);
        state.addGeckolibData(EYE_YAW, tracking
                ? Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -34F, 34F) * Mth.DEG_TO_RAD : 0F);
        state.addGeckolibData(EYE_PITCH, tracking
                ? Mth.clamp(Mth.lerp(partialTick, wanderer.xRotO, wanderer.getXRot()), -20F, 20F) * Mth.DEG_TO_RAD : 0F);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        super.adjustModelBonesForRender(pass, bones);
        float yaw = pass.getOrDefaultGeckolibData(EYE_YAW, 0F), pitch = pass.getOrDefaultGeckolibData(EYE_PITCH, 0F);
        bones.ifPresent("eyeleft", bone -> bone.setRotation(bone.getRotX() + pitch, bone.getRotY() + yaw, bone.getRotZ()));
        bones.ifPresent("eyeright", bone -> bone.setRotation(bone.getRotX() + pitch, bone.getRotY() + yaw, bone.getRotZ()));
    }
}

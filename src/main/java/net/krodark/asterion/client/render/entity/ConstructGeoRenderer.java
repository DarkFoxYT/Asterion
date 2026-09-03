package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.entity.ConstructEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

public final class ConstructGeoRenderer extends GeoEntityRenderer<ConstructEntity, EntityRenderState> {
    private static final DataTicket<Float> LOOK_YAW = DataTickets.create("asterion_construct_look_yaw", Float.class);
    private static final DataTicket<Float> LOOK_PITCH = DataTickets.create("asterion_construct_look_pitch", Float.class);
    public ConstructGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new ConstructGeoModel());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "glowbody",
                Asterion.id("textures/entity/construct.png")) {
            @Override protected float surfaceBrightness(EntityRenderState state) { return 0.9F; }
            @Override protected float emissiveStrength(EntityRenderState state) { return 1.15F; }
            @Override protected boolean enhancedSurface(EntityRenderState state) { return true; }
            @Override protected net.minecraft.resources.Identifier amneticEmissionMesh(EntityRenderState state) {
                return getGeoModel().getModelResource(state);
            }
            @Override protected void renderBone(com.geckolib.renderer.base.RenderPassInfo<EntityRenderState> pass,
                    com.geckolib.cache.model.GeoBone bone,
                    net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                if (bone.name().equals("glowbody")) super.renderBone(pass, bone, tasks);
            }
        });
        shadowRadius = 0.55F;
    }

    @Override
    public void addRenderData(ConstructEntity construct, Void related, EntityRenderState state, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, construct.yBodyRotO, construct.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, construct.yHeadRotO, construct.yHeadRot);
        state.addGeckolibData(LOOK_YAW,
                Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -58.0F, 58.0F) * Mth.DEG_TO_RAD);
        state.addGeckolibData(LOOK_PITCH,
                Mth.clamp(Mth.lerp(partialTick, construct.xRotO, construct.getXRot()), -28.0F, 34.0F)
                        * Mth.DEG_TO_RAD);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        super.adjustModelBonesForRender(pass, bones);
        float yaw = pass.getOrDefaultGeckolibData(LOOK_YAW, 0.0F);
        float pitch = pass.getOrDefaultGeckolibData(LOOK_PITCH, 0.0F);
        // Spread gaze over the nested body -> head hierarchy instead of swivelling one cube.
        bones.ifPresent("body", bone -> bone.setRotation(
                bone.getRotX() + pitch * 0.30F, bone.getRotY() + yaw * 0.32F, bone.getRotZ()));
        bones.ifPresent("head", bone -> bone.setRotation(
                bone.getRotX() + pitch * 0.70F, bone.getRotY() + yaw * 0.68F, bone.getRotZ()));
    }
}

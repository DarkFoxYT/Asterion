package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.entity.ConstructEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class ConstructGeoRenderer extends GeoEntityRenderer<ConstructEntity, EntityRenderState> {
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
}

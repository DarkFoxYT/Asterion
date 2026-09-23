package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.entity.WandererEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class WandererGeoRenderer extends GeoEntityRenderer<WandererEntity, EntityRenderState> {
    private static final DataTicket<Boolean> WATCHING = DataTickets.create("asterion_wanderer_watching", Boolean.class);
    public WandererGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new WandererGeoModel());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "wanderer_eyes",
                Asterion.id("textures/entity/wanderer.png")) {
            @Override protected float emissiveStrength(EntityRenderState state) { return 1.8F; }
            @Override protected void renderBone(RenderPassInfo<EntityRenderState> pass, GeoBone bone,
                    net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                if (pass.getOrDefaultGeckolibData(WATCHING, false)
                        && (bone.name().equals("eyeleft") || bone.name().equals("eyeright"))) super.renderBone(pass, bone, tasks);
            }
        });
        shadowRadius = 0.45F;
    }
    @Override public void addRenderData(WandererEntity wanderer, Void related, EntityRenderState state, float partialTick) {
        state.addGeckolibData(WATCHING, wanderer.isWatching());
    }
}

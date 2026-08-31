package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.RuneBeetleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** Small animated beetle placeholder, ready to swap for the authored rune beetle model. */
public final class RuneBeetleRenderer extends GeoEntityRenderer<RuneBeetleEntity, EntityRenderState> {
    public RuneBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("entity/bombadier_beetle"); }
            @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/entity/bombadier_beetle.png"); }
            @Override public Identifier getAnimationResource(RuneBeetleEntity entity) { return Asterion.id("entity/bombadier_beetle"); }
        });
        shadowRadius = .2F;
    }
    @Override public void adjustRenderPose(RenderPassInfo<EntityRenderState> pass) {
        super.adjustRenderPose(pass);
        pass.poseStack().scale(.55F, .55F, .55F);
    }
}

package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public final class CursedBrazierRenderer extends GeoEntityRenderer<CursedBrazierEntity, EntityRenderState> {
    public CursedBrazierRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/brazier"); }
            @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/brasier.png"); }
            @Override public Identifier getAnimationResource(CursedBrazierEntity entity) { return Asterion.id("entity/bombadier_beetle"); }
        });
        shadowRadius = 1.2F;
    }
    @Override public void addRenderData(CursedBrazierEntity brazier,Void related,EntityRenderState state,float partial) {
        if(brazier.isAlive()&&!brazier.isInWater()) LedAmneticLight.updateItemGlowLight(brazier,
                brazier.position().add(0,.95,0),.18F,1F,.30F,1.8F,8F,false);
        else LedAmneticLight.removeItemGlowLight(brazier);
    }
}

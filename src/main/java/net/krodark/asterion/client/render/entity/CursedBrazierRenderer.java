package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CursedBrazierEntity;
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
}

package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public final class ChainLiftRenderer extends GeoEntityRenderer<ChainLiftEntity, EntityRenderState> {
    public ChainLiftRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/chain_lift"); }
            @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/chain_lift.png"); }
            @Override public Identifier getAnimationResource(ChainLiftEntity lift) { return Asterion.id("block/chain_lift"); }
        });
        shadowRadius = 1.5F;
        withRenderLayer(new ChainLiftChainLayer(this));
    }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(ChainLiftEntity lift) {
        return lift.getBoundingBox().expandTowards(0, Math.max(3, lift.ceiling() - lift.getY()), 0);
    }
}

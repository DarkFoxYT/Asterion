package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class BombadierBeetleGeoRenderer extends GeoEntityRenderer<BombadierBeetleEntity, EntityRenderState> {
    public BombadierBeetleGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new BombadierBeetleGeoModel());
        this.shadowRadius = 0.35F;
    }
}

package net.krodark.asterion.client.render.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import net.krodark.asterion.entity.QueenBeetleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class QueenBeetleGeoRenderer extends GeoEntityRenderer<QueenBeetleEntity, EntityRenderState> {
    public QueenBeetleGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new QueenBeetleGeoModel());
        shadowRadius = 1.3F;
    }
}

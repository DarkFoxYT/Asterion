package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ConstructEntity;
import net.minecraft.resources.Identifier;

public final class ConstructGeoModel extends GeoModel<ConstructEntity> {
    private static final Identifier MODEL = Asterion.id("entity/construct");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/construct.png");
    private static final Identifier ANIMATIONS = Asterion.id("entity/construct");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(ConstructEntity entity) { return ANIMATIONS; }
}

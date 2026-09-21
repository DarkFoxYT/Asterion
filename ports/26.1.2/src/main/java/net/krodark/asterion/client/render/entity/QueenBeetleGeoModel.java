package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.QueenBeetleEntity;
import net.minecraft.resources.Identifier;

public final class QueenBeetleGeoModel extends GeoModel<QueenBeetleEntity> {
    private static final Identifier MODEL = Asterion.id("entity/queen_beetle");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/queen_beetle.png");
    private static final Identifier ANIMATIONS = Asterion.id("entity/queen_beetle");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(QueenBeetleEntity entity) { return ANIMATIONS; }
}

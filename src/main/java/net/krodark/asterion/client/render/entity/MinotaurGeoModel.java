package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.resources.Identifier;

public final class MinotaurGeoModel extends GeoModel<MinotaurEntity> {
    private static final Identifier MODEL = Asterion.id("entity/minotaur");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/minotaur.png");
    private static final Identifier ANIMATIONS = Asterion.id("entity/minotaur");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(MinotaurEntity entity) { return ANIMATIONS; }
}

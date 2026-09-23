package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.WandererEntity;
import net.minecraft.resources.Identifier;

public final class WandererGeoModel extends GeoModel<WandererEntity> {
    private static final Identifier MODEL = Asterion.id("entity/wanderer");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/wanderer.png");
    private static final Identifier ANIMATIONS = Asterion.id("entity/wanderer");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(WandererEntity entity) { return ANIMATIONS; }
}

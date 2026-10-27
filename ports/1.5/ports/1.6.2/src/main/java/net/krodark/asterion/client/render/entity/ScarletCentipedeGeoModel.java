package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.resources.Identifier;

public final class ScarletCentipedeGeoModel extends GeoModel<ScarletCentipedeEntity> {
    private static final Identifier MODEL = Asterion.id("entity/centipede");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/centipede.png");
    private static final Identifier ANIMATIONS = Asterion.id("entity/centipede");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(ScarletCentipedeEntity entity) { return ANIMATIONS; }
}

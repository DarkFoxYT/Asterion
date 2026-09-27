package net.krodark.asterion.client.render.block;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.SkeletonBlockEntity;
import net.minecraft.resources.Identifier;

public final class SkeletonGeoModel extends GeoModel<SkeletonBlockEntity> {
    private static final Identifier MODEL = Asterion.id("block/skeleton");
    private static final Identifier TEXTURE = Asterion.id("textures/block/skeleton.png");
    private static final Identifier ANIMATION = Asterion.id("block/skeleton");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(SkeletonBlockEntity animatable) { return ANIMATION; }
}

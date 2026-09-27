package net.krodark.asterion.client.render.block;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ShatteredDeadWoodBlockEntity;
import net.minecraft.resources.Identifier;

public final class ShatteredDeadWoodGeoModel extends GeoModel<ShatteredDeadWoodBlockEntity> {
    private static final Identifier MODEL = Asterion.id("block/shattered_dead_wood");
    private static final Identifier TEXTURE = Asterion.id("textures/block/shattered_dead_wood.png");
    private static final Identifier ANIMATION = Asterion.id("block/shattered_dead_wood");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(ShatteredDeadWoodBlockEntity animatable) {
        return ANIMATION;
    }
}

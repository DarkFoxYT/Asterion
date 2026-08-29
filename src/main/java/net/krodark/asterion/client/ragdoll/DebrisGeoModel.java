package net.krodark.asterion.client.ragdoll;

import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

final class DebrisGeoModel extends GeoModel<DebrisPhysicsObject> {
    private static final Identifier MODEL = Asterion.id("physics/debris1");
    private static final Identifier TEXTURE = Asterion.id("textures/physics/debris1.png");
    private static final Identifier ANIMATION = Asterion.id("physics/debris1");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(DebrisPhysicsObject animatable) {
        return ANIMATION;
    }
}

package net.krodark.asterion.client.ragdoll;

import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

final class DebrisGeoModel extends GeoModel<DebrisPhysicsObject> {
    static final DataTicket<Integer> VARIANT = DataTickets.create("asterion_debris_variant", Integer.class);
    private static final Identifier MODEL_1 = Asterion.id("physics/debris1");
    private static final Identifier MODEL_2 = Asterion.id("physics/debris2");
    private static final Identifier TEXTURE_1 = Asterion.id("textures/physics/debris1.png");
    private static final Identifier TEXTURE_2 = Asterion.id("textures/physics/debris2.png");
    private static final Identifier ANIMATION = Asterion.id("physics/debris1");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(VARIANT, 1) == 2 ? MODEL_2 : MODEL_1;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(VARIANT, 1) == 2 ? TEXTURE_2 : TEXTURE_1;
    }

    @Override
    public Identifier getAnimationResource(DebrisPhysicsObject animatable) {
        return ANIMATION;
    }
}

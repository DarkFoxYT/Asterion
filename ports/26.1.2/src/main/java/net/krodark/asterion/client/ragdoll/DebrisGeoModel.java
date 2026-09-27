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
    private static final Identifier MODEL_3 = Asterion.id("physics/debris3");
    private static final Identifier MODEL_4 = Asterion.id("physics/debris4");
    private static final Identifier MODEL_5 = Asterion.id("physics/debris5");
    private static final Identifier MODEL_6 = Asterion.id("physics/debris6");
    private static final Identifier TEXTURE_1 = Asterion.id("textures/physics/debris1.png");
    private static final Identifier TEXTURE_2 = Asterion.id("textures/physics/debris2.png");
    private static final Identifier TEXTURE_3 = Asterion.id("textures/physics/debris3.png");
    private static final Identifier TEXTURE_4 = Asterion.id("textures/physics/debris4.png");
    private static final Identifier TEXTURE_5 = Asterion.id("textures/physics/debris5.png");
    private static final Identifier TEXTURE_6 = Asterion.id("textures/physics/debris6.png");
    private static final Identifier ANIMATION = Asterion.id("physics/debris1");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return switch (renderState.getOrDefaultGeckolibData(VARIANT, 1)) {
            case 9 -> Asterion.id("physics/sword");
            case 8 -> Asterion.id("physics/axe");
            case 7 -> Asterion.id("physics/minotaur_door_debirs");
            case 2 -> MODEL_2;
            case 3 -> MODEL_3;
            case 4 -> MODEL_4;
            case 5 -> MODEL_5;
            case 6 -> MODEL_6;
            default -> MODEL_1;
        };
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return switch (renderState.getOrDefaultGeckolibData(VARIANT, 1)) {
            case 9 -> Asterion.id("textures/physics/sword.png");
            case 8 -> Asterion.id("textures/physics/axe.png");
            case 7 -> Asterion.id("textures/physics/minotaur_door_debris.png");
            case 2 -> TEXTURE_2;
            case 3 -> TEXTURE_3;
            case 4 -> TEXTURE_4;
            case 5 -> TEXTURE_5;
            case 6 -> TEXTURE_6;
            default -> TEXTURE_1;
        };
    }

    @Override
    public Identifier getAnimationResource(DebrisPhysicsObject animatable) {
        return ANIMATION;
    }
}

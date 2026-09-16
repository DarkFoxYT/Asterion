package net.krodark.asterion.update.underworld.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public final class CharonsFerryRenderer extends GeoEntityRenderer<CharonsFerryEntity, EntityRenderState> {
    public CharonsFerryRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) {
                return Asterion.id("underworld/entity/charons_ferry");
            }
            @Override public Identifier getTextureResource(GeoRenderState state) {
                return Asterion.id("textures/underworld/entity/charons_ferry.png");
            }
            @Override public Identifier getAnimationResource(CharonsFerryEntity entity) {
                return Asterion.id("underworld/entity/charons_ferry");
            }
        });
        shadowRadius = 1.8F;
    }
}

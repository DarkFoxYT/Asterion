package net.krodark.asterion.update.underworld.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** Single model pass, including the hood: no coincident overlay geometry. */
public final class CharonRenderer extends GeoEntityRenderer<CharonEntity, EntityRenderState> {
    private static final Identifier MODEL = Asterion.id("entity/charon");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/charon.png");

    public CharonRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
            @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
            @Override public Identifier getAnimationResource(CharonEntity entity) { return Asterion.id("entity/charon"); }
        });
        shadowRadius = .55F;
    }
}

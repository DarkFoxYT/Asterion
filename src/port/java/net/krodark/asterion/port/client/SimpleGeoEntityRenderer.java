package net.krodark.asterion.port.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Basic GeckoLib 4 renderer used while the newer render-state effects are unavailable. */
public final class SimpleGeoEntityRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    public SimpleGeoEntityRenderer(EntityRendererProvider.Context context, ResourceLocation model,
                                   ResourceLocation texture, ResourceLocation animations,
                                   float shadowRadius, float scale) {
        super(context, new StaticModel<>(model, texture, animations));
        this.shadowRadius = shadowRadius;
        this.scaleWidth = scale;
        this.scaleHeight = scale;
    }

    @SuppressWarnings("deprecation")
    private static final class StaticModel<T extends GeoAnimatable> extends GeoModel<T> {
        private final ResourceLocation model;
        private final ResourceLocation texture;
        private final ResourceLocation animations;

        private StaticModel(ResourceLocation model, ResourceLocation texture, ResourceLocation animations) {
            this.model = model;
            this.texture = texture;
            this.animations = animations;
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return animations;
        }
    }
}

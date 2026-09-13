package net.krodark.asterion.port.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Basic GeckoLib 4 renderer used while the newer render-state effects are unavailable. */
public class SimpleGeoEntityRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    public SimpleGeoEntityRenderer(EntityRendererProvider.Context context, ResourceLocation model,
                                   ResourceLocation texture, ResourceLocation animations,
                                   float shadowRadius, float scale) {
        super(context, new StaticModel<>(model, texture, animations));
        this.shadowRadius = shadowRadius;
        this.scaleWidth = scale;
        this.scaleHeight = scale;
    }

    public SimpleGeoEntityRenderer<T> withEmissiveBones(String... bones) {
        return withEmissiveBones(ignored -> 0xFFFFFFFF, ignored -> true, bones);
    }

    public SimpleGeoEntityRenderer<T> withEmissiveBones(ToIntFunction<T> color,
                                                         Predicate<T> visible, String... bones) {
        addRenderLayer(new PortEmissiveGeoLayer<>(this, this::getTextureLocation, color, visible, bones));
        return this;
    }

    @Override
    public boolean shouldRender(T animatable, Frustum frustum, double x, double y, double z) {
        // Animated GeoModel bones often extend well beyond the vanilla hitbox.
        // Keep the model submitted when that small box leaves the view so large
        // limbs, doors, chains and wall-crawling bodies do not pop out.
        return true;
    }

    @SuppressWarnings("deprecation")
    private static final class StaticModel<T extends GeoAnimatable> extends GeoModel<T> {
        private final ResourceLocation model;
        private final ResourceLocation texture;
        private final ResourceLocation animations;

        private StaticModel(ResourceLocation model, ResourceLocation texture, ResourceLocation animations) {
            this.model = geckoModel(model);
            this.texture = texture;
            this.animations = geckoAnimation(animations);
        }

        private static ResourceLocation geckoModel(ResourceLocation id) {
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "geo/" + id.getPath() + ".geo.json");
        }

        private static ResourceLocation geckoAnimation(ResourceLocation id) {
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "animations/" + id.getPath() + ".animation.json");
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

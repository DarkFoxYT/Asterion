package net.krodark.asterion.port.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.function.Function;

/** Minecraft 1.21.1 GeckoLib renderer used by both loader client bootstraps. */
public class SimpleGeoBlockRenderer<T extends BlockEntity & GeoAnimatable>
        extends GeoBlockRenderer<T> {
    public SimpleGeoBlockRenderer(ResourceLocation model, ResourceLocation texture,
                                  ResourceLocation animation) {
        this(model, ignored -> texture, ignored -> animation);
    }

    public SimpleGeoBlockRenderer(ResourceLocation model, Function<T, ResourceLocation> texture,
                                  Function<T, ResourceLocation> animation) {
        this(ignored -> model, texture, animation);
    }

    public SimpleGeoBlockRenderer(Function<T, ResourceLocation> model,
                                  Function<T, ResourceLocation> texture,
                                  Function<T, ResourceLocation> animation) {
        super(new StaticModel<>(model, texture, animation));
    }

    @SuppressWarnings("deprecation")
    private static final class StaticModel<T extends GeoAnimatable> extends GeoModel<T> {
        private final Function<T, ResourceLocation> model;
        private final Function<T, ResourceLocation> texture;
        private final Function<T, ResourceLocation> animation;

        private StaticModel(Function<T, ResourceLocation> model, Function<T, ResourceLocation> texture,
                            Function<T, ResourceLocation> animation) {
            this.model = model;
            this.texture = texture;
            this.animation = animation;
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return normalize(model.apply(animatable), "geo/", ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return texture.apply(animatable);
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            ResourceLocation id = animation.apply(animatable);
            return id == null ? null : normalize(id, "animations/", ".animation.json");
        }

        private static ResourceLocation normalize(ResourceLocation id, String prefix, String suffix) {
            String path = id.getPath();
            if (!path.startsWith(prefix)) path = prefix + path;
            if (!path.endsWith(suffix)) path += suffix;
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
        }
    }
}

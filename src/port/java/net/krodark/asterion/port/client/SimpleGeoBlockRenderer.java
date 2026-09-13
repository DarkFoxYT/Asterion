package net.krodark.asterion.port.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

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

    public SimpleGeoBlockRenderer<T> withEmissiveBones(String... bones) {
        return withEmissiveBones(ignored -> 0xFFFFFFFF, ignored -> true, bones);
    }

    public SimpleGeoBlockRenderer<T> withEmissiveBones(ToIntFunction<T> color,
                                                        Predicate<T> visible, String... bones) {
        addRenderLayer(new PortEmissiveGeoLayer<>(this, this::getTextureLocation, color, visible, bones));
        return this;
    }

    @Override
    public boolean shouldRenderOffScreen(T animatable) {
        // Multi-block and animated GeoModels frequently extend outside their
        // root block's section bounds; keep visible bones from popping out.
        return true;
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

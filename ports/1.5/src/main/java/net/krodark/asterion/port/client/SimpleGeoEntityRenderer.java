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
        addRenderLayer(new PortEmissiveGeoLayer<>(this, entity -> getGeoModel().getTextureResource(entity), color, visible, bones));
        return this;
    }

    @Override
    public void render(T entity,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        if(entity instanceof net.minecraft.world.entity.LivingEntity living && PortRagdolls.isRagdolled(living)) return;
        super.render(entity,yaw,partial,poses,buffers,light);
    }

    private record CullBounds(int tick, net.minecraft.world.phys.AABB bounds) {}
    private final java.util.Map<Entity, CullBounds> articulatedBounds = new java.util.WeakHashMap<>();

    @Override
    public boolean shouldRender(T animatable, Frustum frustum, double x, double y, double z) {
        if (!animatable.shouldRender(x, y, z)) return false;
        // Animated limbs need a generous margin, but off-screen models must not
        // run their full GeckoLib animation and emissive replay every frame.
        var bounds = animatable.getBoundingBox().inflate(Math.max(3.0, Math.max(scaleWidth, scaleHeight) * 3.0));
        if (animatable instanceof net.krodark.asterion.entity.ScarletCentipedeEntity centipede) {
            var cached = articulatedBounds.get(centipede);
            if (cached == null || cached.tick() != centipede.tickCount) {
                for (int i = 0; i < centipede.chainSegmentCount(); i++) {
                    var point = centipede.chainPose(i, 1).position();
                    bounds = bounds.minmax(new net.minecraft.world.phys.AABB(point, point).inflate(3.0));
                }
                cached = new CullBounds(centipede.tickCount, bounds);
                articulatedBounds.put(centipede, cached);
            }
            bounds = cached.bounds();
        }
        return frustum.isVisible(bounds);
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
        public void setCustomAnimations(T entity,long instanceId,software.bernie.geckolib.animation.AnimationState<T> state) {
            super.setCustomAnimations(entity,instanceId,state);
            if(entity instanceof net.krodark.asterion.entity.MinotaurEntity boss)
                PortMinotaurPose.apply(boss,getAnimationProcessor().getRegisteredBones(),state.getPartialTick());
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

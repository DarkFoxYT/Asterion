package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Renders selected GeckoLib bones through the shared Veil emission buffer. */
public final class PortEmissiveGeoLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final Set<String> bones;
    private final Function<T, ResourceLocation> texture;
    private final ToIntFunction<T> color;
    private final Predicate<T> visible;
    private int renderTint;
    private boolean renderVisible;
    private ResourceLocation renderTexture;

    public PortEmissiveGeoLayer(GeoRenderer<T> renderer, Function<T, ResourceLocation> texture,
                                ToIntFunction<T> color, Predicate<T> visible, String... bones) {
        super(renderer);
        this.bones = Set.of(bones);
        this.texture = texture;
        this.color = color;
        this.visible = visible;
    }

    @Override
    public void preRender(PoseStack poses, T animatable, BakedGeoModel model, RenderType renderType,
                          MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        renderTint = color.applyAsInt(animatable);
        renderVisible = visible.test(animatable) && (renderTint >>> 24) != 0;
        renderTexture = renderVisible ? texture.apply(animatable) : null;
        for (String name : bones) model.getBone(name).ifPresent(bone -> bone.setHidden(!renderVisible));
    }

    @Override
    public void renderForBone(PoseStack poses, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!renderVisible || !bones.contains(bone.getName())) return;

        // Replay at the end of the entity stage, after every GeoModel has
        // contributed its normal surface to the shared world depth buffer.
        PortEmissiveQueue.submit(renderer, bone, poses.last(), renderTexture,
                renderTint, packedOverlay);
    }

    @Override
    public void render(PoseStack poses, T animatable, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        // Rendering is deliberately deferred by PortEmissiveQueue.
    }
}

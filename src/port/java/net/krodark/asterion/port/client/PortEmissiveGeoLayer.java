package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
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
        // The normal surface must not also draw these cubes; the emission type
        // supplies both its visible surface and its separate bloom attachment.
        for (String name : bones) model.getBone(name).ifPresent(bone -> bone.setHidden(true));
    }

    @Override
    public void renderForBone(PoseStack poses, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!bones.contains(bone.getName()) || !visible.test(animatable)) return;
        int tint = color.applyAsInt(animatable);
        if ((tint >>> 24) == 0) return;

        RenderType emission = PortEmissiveBuffer.renderType(texture.apply(animatable));
        VertexConsumer out = buffers.getBuffer(emission);
        boolean hidden = bone.isHidden();
        bone.setHidden(false);
        try {
            renderer.renderCubesOfBone(poses, bone, out, LightTexture.FULL_BRIGHT, packedOverlay, tint);
        } finally {
            bone.setHidden(hidden);
            // GeckoLib requires the original consumer to be made current again
            // after a per-bone layer switches render types.
            buffers.getBuffer(renderType);
        }
    }
}

package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.List;
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
    private final List<Draw> pending = new ArrayList<>();

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
        pending.clear();
        boolean show = visible.test(animatable) && (color.applyAsInt(animatable) >>> 24) != 0;
        for (String name : bones) model.getBone(name).ifPresent(bone -> bone.setHidden(!show));
    }

    @Override
    public void renderForBone(PoseStack poses, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!bones.contains(bone.getName()) || !visible.test(animatable)) return;
        int tint = color.applyAsInt(animatable);
        if ((tint >>> 24) == 0) return;

        // Capture the posed bone, then emit only after the complete base model.
        // The emissive bones remain visible in that base pass, so they contribute
        // their own depth instead of glowing through the rest of the GeoModel.
        pending.add(new Draw(bone, new Matrix4f(poses.last().pose()),
                new Matrix3f(poses.last().normal()), tint));
    }

    @Override
    public void render(PoseStack poses, T animatable, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        if (pending.isEmpty()) return;
        RenderType emission = PortEmissiveBuffer.renderType(texture.apply(animatable));
        VertexConsumer out = buffers.getBuffer(emission);
        PoseStack drawPoses = new PoseStack();
        for (Draw draw : pending) {
            drawPoses.last().pose().set(draw.pose);
            drawPoses.last().normal().set(draw.normal);
            renderer.renderCubesOfBone(drawPoses, draw.bone, out,
                    LightTexture.FULL_BRIGHT, packedOverlay, draw.tint);
        }
        pending.clear();
        buffers.getBuffer(renderType);
    }

    private record Draw(GeoBone bone, Matrix4f pose, Matrix3f normal, int tint) {}
}

package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.LiftCallRuneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Billboarded, full-bright lift call rune for both 1.21.1 loaders. */
public final class LiftCallRunePortRenderer extends EntityRenderer<LiftCallRuneEntity> {
    private static final ResourceLocation TEXTURE = Asterion.id("textures/pin/elevator_call.png");

    public LiftCallRunePortRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LiftCallRuneEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.translate(0, .35 + Math.sin((entity.tickCount + partialTick) * .045) * .035, 0);
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        poses.scale(.62F, .62F, .62F);
        Matrix4f matrix = poses.last().pose();
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        vertex(out, matrix, -.5F, -.5F, 0, 1);
        vertex(out, matrix, .5F, -.5F, 1, 1);
        vertex(out, matrix, .5F, .5F, 1, 0);
        vertex(out, matrix, -.5F, .5F, 0, 0);
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float u, float v) {
        out.addVertex(matrix, x, y, 0)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(LiftCallRuneEntity entity) {
        return TEXTURE;
    }
}

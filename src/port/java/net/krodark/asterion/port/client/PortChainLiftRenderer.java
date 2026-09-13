package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Lift renderer with a cheap two-plane chain stretched to the synchronized ceiling anchor. */
public final class PortChainLiftRenderer extends SimpleGeoEntityRenderer<ChainLiftEntity> {
    private static final RenderType CHAIN = RenderType.entityCutout(Asterion.id("textures/block/mazesteel_chain.png"));

    public PortChainLiftRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("block/chain_lift"), Asterion.id("textures/block/chain_lift.png"),
                Asterion.id("block/chain_lift"), 1.5F, 1.0F);
        addRenderLayer(new ChainLayer(this));
    }

    private static final class ChainLayer extends GeoRenderLayer<ChainLiftEntity> {
        private ChainLayer(PortChainLiftRenderer renderer) { super(renderer); }

        @Override
        public void renderForBone(PoseStack poses, ChainLiftEntity lift, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            if (!bone.getName().equals("chain")) return;
            double liftY = Mth.lerp(partialTick, lift.yo, lift.getY());
            // The authored empty chain bone sits at Y=46px. Rendering here makes
            // the chain inherit every animation/rotation applied to its holder.
            float length = (float)(lift.ceiling() - liftY - 46.0D / 16.0D);
            if (!Float.isFinite(length) || length <= 0 || length > 256) return;
            VertexConsumer out = buffers.getBuffer(CHAIN);
            Matrix4f matrix = poses.last().pose();
            float width = .12F;
            float v = length / .5F;
            quad(out, matrix, -width, 0, 0, width, length, 0, 0, v, packedLight, 0, 0, 1);
            quad(out, matrix, 0, 0, -width, 0, length, width, 0, v, packedLight, 1, 0, 0);
            buffers.getBuffer(renderType);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f matrix, float x0, float y0, float z0,
                             float x1, float y1, float z1, float u0, float v1, int light,
                             float nx, float ny, float nz) {
        vertex(out, matrix, x0, y0, z0, u0, 0, light, nx, ny, nz);
        vertex(out, matrix, x1, y0, z1, 1, 0, light, nx, ny, nz);
        vertex(out, matrix, x1, y1, z1, 1, v1, light, nx, ny, nz);
        vertex(out, matrix, x0, y1, z0, u0, v1, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz) {
        out.addVertex(matrix, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    @Override
    public boolean shouldRender(ChainLiftEntity lift, Frustum frustum, double x, double y, double z) {
        AABB bounds = lift.getBoundingBox().expandTowards(0,
                Math.max(3.0D, lift.ceiling() - lift.getY()), 0).inflate(.5D);
        return frustum.isVisible(bounds);
    }
}

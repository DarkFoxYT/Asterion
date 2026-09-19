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
            poses.pushPose();
            // GeckoLib 4 invokes layers after moving away from the bone pivot.
            //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poses, bone);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(poses, bone);
            *///?}
            org.joml.Vector3f point = poses.last().pose().transformPosition(new org.joml.Vector3f());
            poses.popPose();
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            var start = new net.minecraft.world.phys.Vec3(point.x, point.y, point.z);
            var end = new net.minecraft.world.phys.Vec3(Mth.lerp(partialTick, lift.xo, lift.getX()),
                    lift.ceiling(), Mth.lerp(partialTick, lift.zo, lift.getZ())).subtract(camera);
            if (end.y <= start.y || start.distanceToSqr(end) > 256 * 256) return;
            PortMinotaurChainLayer.draw(buffers.getBuffer(CHAIN), start, end, 0, packedLight);
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

//? if >=1.20.5 {
out.addVertex(matrix, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
//?} else {
/*out.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();*/
//?}

    }

    @Override
    public boolean shouldRender(ChainLiftEntity lift, Frustum frustum, double x, double y, double z) {
        AABB bounds = lift.getBoundingBox().inflate(3).minmax(new AABB(
                lift.getX() - 1, lift.ceiling() - 1, lift.getZ() - 1,
                lift.getX() + 1, lift.ceiling() + 1, lift.getZ() + 1));
        return frustum.isVisible(bounds);
    }
}

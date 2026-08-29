package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class LabyrinthVineGeoRenderer
        extends GeoBlockRenderer<LabyrinthVineBlockEntity, BlockEntityRenderState> {
    private static final Identifier TEXTURE = Asterion.id("textures/block/labyrinth_vine.png");
    private static final String EMISSIVE_BONE = "glow";

    public LabyrinthVineGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new LabyrinthVineGeoModel());
        // Only the authored `glow` bone is redirected into Amnetic. The parent `head`
        // remains in GeckoLib's ordinary model pass and never touches the emissive target.
        withRenderLayer(new CustomBoneTextureGeoLayer<>(this, EMISSIVE_BONE, TEXTURE) {
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true);
            }
            @Override protected RenderType getRenderType(BlockEntityRenderState state, Identifier texture) {
                return LedAmneticLight.bloomRenderLayer(texture);
            }
        });
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<BlockEntityRenderState> pass) {
        // The authored model points toward DOWN. Rotate around the block center so every
        // direction stays inside its voxel and the bulb lands on the exposed tip.
        PoseStack pose = pass.poseStack();
        net.minecraft.core.Direction facing = pass.getOrDefaultGeckolibData(
                GeoBlockRenderer.DIRECTION_FACING, net.minecraft.core.Direction.DOWN);
        pose.translate(0.0D, 0.5D, 0.0D);
        switch (facing) {
            case UP -> pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            case EAST -> pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
            case WEST -> pose.mulPose(Axis.ZN.rotationDegrees(90.0F));
            case SOUTH -> pose.mulPose(Axis.XN.rotationDegrees(90.0F));
            case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> { }
        }
        pose.translate(0.0D, -0.5D, 0.0D);
    }

    @Override
    public void addRenderData(LabyrinthVineBlockEntity vine, Void relatedObject,
                              BlockEntityRenderState state, float partialTick) {
        boolean end = vine.isEnd();
        state.addGeckolibData(LabyrinthVineGeoModel.END, end);
        if (end) {
            Vec3 direction = Vec3.atLowerCornerOf(vine.getBlockState()
                    .getValue(net.krodark.asterion.block.LabyrinthVineBlock.FACING).getUnitVec3i());
            LedAmneticLight.updateItemGlowLight(vine,
                    Vec3.atCenterOf(vine.getBlockPos()).add(direction.scale(0.22D)),
                    1.0F, 0.48F, 0.12F, 1.45F, 6.5F, false);
        } else LedAmneticLight.removeItemGlowLight(vine);
    }
}

package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.PedestalBlock;
import net.krodark.asterion.block.PedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Restores Afterblow above an unclaimed pedestal. */
public final class PortPedestalRenderer extends SimpleGeoBlockRenderer<PedestalBlockEntity> {
    public PortPedestalRenderer(BlockEntityRendererProvider.Context context) {
        super(Asterion.id("block/pedestal"), Asterion.id("textures/block/pedestal.png"), null);
    }

    @Override

//? if >=1.20.5 {
public void render(PedestalBlockEntity pedestal, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
//?} else {
/*public void renderTyped(PedestalBlockEntity pedestal, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {*/
//?}

        //? if >=1.20.5 {
super.render(pedestal, partialTick, poses, buffers, packedLight, packedOverlay);
//?} else {
/*super.renderTyped(pedestal, partialTick, poses, buffers, packedLight, packedOverlay);*/
//?}

        if (pedestal.getBlockState().getValue(PedestalBlock.CLAIMED)) return;
        poses.pushPose();
        poses.translate(.5D, 1.28D, .5D);
        poses.mulPose(Axis.YP.rotationDegrees(45));
        poses.mulPose(Axis.ZP.rotationDegrees(-135));
        poses.scale(1.25F, 1.25F, 1.25F);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(Asterion.AFTERBLOW),
                ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poses, buffers, pedestal.getLevel(), (int)pedestal.getBlockPos().asLong());
        poses.popPose();
    }
}

package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

public final class PedestalRenderer extends GeoBlockRenderer<PedestalBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<ItemStackRenderState> SWORD = DataTickets.create("asterion_pedestal_sword", ItemStackRenderState.class);
    public PedestalRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/pedestal"); }
            @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/pedestal.png"); }
            @Override public Identifier getAnimationResource(PedestalBlockEntity block) { return null; }
        });
    }
    @Override public void addRenderData(PedestalBlockEntity pedestal, Void unused, BlockEntityRenderState state, float partial) {
        var sword = state.getOrDefaultGeckolibData(SWORD, (ItemStackRenderState)null);
        if (sword == null) sword = new ItemStackRenderState();
        sword.clear();
        if (!pedestal.getBlockState().getValue(PedestalBlock.CLAIMED))
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(sword, new ItemStack(Asterion.AFTERBLOW),
                    ItemDisplayContext.NONE, pedestal.getLevel(), null, 0);
        state.addGeckolibData(SWORD, sword);
    }
    @Override public void submit(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {
        super.submit(state, poses, tasks, camera);
        var sword = state.getOrDefaultGeckolibData(SWORD, (ItemStackRenderState)null);
        if (sword == null || sword.isEmpty()) return;
        poses.pushPose();
        poses.translate(.5, 1.28, .5);
        poses.mulPose(Axis.YP.rotationDegrees(45));
        poses.mulPose(Axis.ZP.rotationDegrees(-135));
        poses.scale(1.25F, 1.25F, 1.25F);
        sword.submit(poses, tasks, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
    }
}

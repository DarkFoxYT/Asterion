package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 1.21.1 fallback for the newer submitted-geometry physics weapon renderer. */
public final class MinotaurAxePortRenderer extends EntityRenderer<MinotaurAxeEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public MinotaurAxePortRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = .6F;
    }

    @Override
    public void render(MinotaurAxeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(entity.renderRotation(partialTick));
        float scale = entity.modelScale() * (entity.isSword() ? 3.2F : 4.0F);
        poses.scale(scale, scale, scale);
        ItemStack stack = new ItemStack(entity.isSword() ? Items.IRON_SWORD : Items.IRON_AXE);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poses, buffers, entity.level(), entity.getId());
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MinotaurAxeEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}

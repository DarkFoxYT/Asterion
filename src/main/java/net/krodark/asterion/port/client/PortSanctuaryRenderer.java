package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.SanctuaryBlock;
import net.krodark.asterion.block.SanctuaryBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;

/** Dedicated sanctuary renderer; only the root block renders and it owns no door animation state. */
public final class PortSanctuaryRenderer extends SimpleGeoBlockRenderer<SanctuaryBlockEntity> {
    private boolean altar;
    private int charge;
    private float time;

    public PortSanctuaryRenderer() {
        super(entity -> Asterion.id(((SanctuaryBlock)entity.getBlockState().getBlock()).altar
                        ? "block/respawn_altar" : "block/respawn_obelisk"),
                entity -> texture(entity), ignored -> null);
        withEmissiveBones(PortSanctuaryRenderer::glowColor,
                PortSanctuaryRenderer::hasGlow, "glow");
    }

    private static boolean hasGlow(SanctuaryBlockEntity entity) {
        SanctuaryBlock block = (SanctuaryBlock)entity.getBlockState().getBlock();
        return block.altar ? entity.getBlockState().getValue(SanctuaryBlock.CHARGE) == 1
                : entity.clientGlowAlpha() > .005F;
    }

    private static int glowColor(SanctuaryBlockEntity entity) {
        SanctuaryBlock block = (SanctuaryBlock)entity.getBlockState().getBlock();
        int alpha = block.altar ? 255 : net.krodark.asterion.port.compat.MathCompat.clamp(Math.round(entity.clientGlowAlpha() * 255F), 0, 255);
        return alpha << 24 | 0xFFE7B5;
    }

    private static ResourceLocation texture(SanctuaryBlockEntity entity) {
        SanctuaryBlock block = (SanctuaryBlock)entity.getBlockState().getBlock();
        if (!block.altar) return Asterion.id("textures/block/respawn_obelisk.png");
        return ResourceLocation.withDefaultNamespace("textures/block/"
                + (entity.getBlockState().getValue(SanctuaryBlock.CHARGE) == 1 ? "gold_block.png" : "iron_block.png"));
    }

    @Override

//? if >=1.20.5 {
public void render(SanctuaryBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
//?} else {
/*public void renderTyped(SanctuaryBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {*/
//?}

        SanctuaryBlock block = (SanctuaryBlock)entity.getBlockState().getBlock();
        altar = block.altar;
        charge = entity.getBlockState().getValue(SanctuaryBlock.CHARGE);
        time = (entity.getLevel() == null ? 0 : entity.getLevel().getGameTime()) + partialTick;
        //? if >=1.20.5 {
super.render(entity, partialTick, poses, buffers, light, overlay);
//?} else {
/*super.renderTyped(entity, partialTick, poses, buffers, light, overlay);*/
//?}

    }

    @Override

//? if >=1.20.5 {
public void renderRecursively(PoseStack poses, SanctuaryBlockEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean rerender, float partialTick, int light, int overlay, int colour) {
//?} else {
/*public void renderRecursively(PoseStack poses, SanctuaryBlockEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean rerender, float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int colour = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

        if (bone.getName().equals("glow")) {
            if (altar) {
                bone.setPosY(bone.getInitialSnapshot().getOffsetY() + (float)Math.sin(time * .065D) * 1.2F);
                bone.setRotY(bone.getInitialSnapshot().getRotY() + time * .025F);
                bone.setRotZ(bone.getInitialSnapshot().getRotZ() + .15F);
            }
        }

//? if >=1.20.5 {
super.renderRecursively(poses, entity, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
//?} else {
/*super.renderRecursively(poses, entity, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, ((colour >> 16) & 255) / 255F, ((colour >> 8) & 255) / 255F, (colour & 255) / 255F, ((colour >>> 24) & 255) / 255F);*/
//?}

    }

    @Override public boolean shouldRender(SanctuaryBlockEntity entity, Vec3 camera) {
        SanctuaryBlock block = (SanctuaryBlock)entity.getBlockState().getBlock();
        return block.isRoot(entity.getBlockState()) && super.shouldRender(entity, camera);
    }

    @Override public int getViewDistance() { return 96; }
}

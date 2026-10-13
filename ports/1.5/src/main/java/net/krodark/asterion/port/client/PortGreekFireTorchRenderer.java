package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.GreekFireTorchBlock;
import net.krodark.asterion.block.GreekFireTorchBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/** Correct floor/wall torch geometry with a depth-aware emissive flame. */
public final class PortGreekFireTorchRenderer extends SimpleGeoBlockRenderer<GreekFireTorchBlockEntity> {
    private static final net.minecraft.resources.ResourceLocation FLOOR_MODEL=Asterion.id("block/floor_torch");
    private static final net.minecraft.resources.ResourceLocation WALL_MODEL=Asterion.id("block/wall_torch");
    private static final net.minecraft.resources.ResourceLocation ANIMATION=Asterion.id("block/greek_fire_torch");
    private static final net.minecraft.resources.ResourceLocation UNLIT_TEXTURE=Asterion.id("textures/block/torch_no_fire.png");
    private static final net.minecraft.resources.ResourceLocation[] FIRE_TEXTURES=java.util.Arrays.stream(GreekFireTorchBlock.FireColor.values())
            .map(color -> Asterion.id("textures/block/"+color.texture+".png")).toArray(net.minecraft.resources.ResourceLocation[]::new);
    public PortGreekFireTorchRenderer() {
        super(entity -> {
            GreekFireTorchBlock block = (GreekFireTorchBlock)entity.getBlockState().getBlock();
            return block.wall ? WALL_MODEL : FLOOR_MODEL;
        }, entity -> {
            var state = entity.getBlockState();
            GreekFireTorchBlock block = (GreekFireTorchBlock)state.getBlock();
            return state.getValue(GreekFireTorchBlock.LIT) ? FIRE_TEXTURES[block.fireColor.ordinal()] : UNLIT_TEXTURE;
        }, ignored -> ANIMATION);
        withEmissiveBones(ignored -> 0xFFFFFFFF, PortGreekFireTorchRenderer::hasFlame, "flame");
    }

    private static boolean hasFlame(GreekFireTorchBlockEntity entity) {
        var state = entity.getBlockState();
        GreekFireTorchBlock block = (GreekFireTorchBlock)state.getBlock();
        return state.getValue(GreekFireTorchBlock.LIT)
                && (block.wall || state.getValue(GreekFireTorchBlock.TOP));
    }

    @Override

//? if >=1.20.5 {
public void preRender(PoseStack poses, GreekFireTorchBlockEntity torch, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, int color) {
//?} else {
/*public void preRender(PoseStack poses, GreekFireTorchBlockEntity torch, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int color = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

        GreekFireTorchBlock block = (GreekFireTorchBlock)torch.getBlockState().getBlock();
        boolean top = torch.getBlockState().getValue(GreekFireTorchBlock.TOP);
        model.getBone("shaft").ifPresent(bone -> bone.setHidden(!block.wall && top));
        model.getBone("top").ifPresent(bone -> bone.setHidden(!block.wall && !top));

//? if >=1.20.5 {
super.preRender(poses, torch, model, buffers, buffer, rerender, partialTick, light, overlay, color);
//?} else {
/*super.preRender(poses, torch, model, buffers, buffer, rerender, partialTick, light, overlay, ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, ((color >>> 24) & 255) / 255F);*/
//?}

    }
}

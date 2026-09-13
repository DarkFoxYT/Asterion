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
    public PortGreekFireTorchRenderer() {
        super(entity -> {
            GreekFireTorchBlock block = (GreekFireTorchBlock)entity.getBlockState().getBlock();
            return Asterion.id(block.wall ? "block/wall_torch" : "block/floor_torch");
        }, entity -> {
            var state = entity.getBlockState();
            GreekFireTorchBlock block = (GreekFireTorchBlock)state.getBlock();
            return Asterion.id("textures/block/" + (state.getValue(GreekFireTorchBlock.LIT)
                    ? block.fireColor.texture : "torch_no_fire") + ".png");
        }, ignored -> Asterion.id("block/greek_fire_torch"));
        withEmissiveBones(ignored -> 0xFFFFFFFF, PortGreekFireTorchRenderer::hasFlame, "flame");
    }

    private static boolean hasFlame(GreekFireTorchBlockEntity entity) {
        var state = entity.getBlockState();
        GreekFireTorchBlock block = (GreekFireTorchBlock)state.getBlock();
        return state.getValue(GreekFireTorchBlock.LIT)
                && (block.wall || state.getValue(GreekFireTorchBlock.TOP));
    }

    @Override
    public void preRender(PoseStack poses, GreekFireTorchBlockEntity torch, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, int color) {
        GreekFireTorchBlock block = (GreekFireTorchBlock)torch.getBlockState().getBlock();
        boolean top = torch.getBlockState().getValue(GreekFireTorchBlock.TOP);
        model.getBone("shaft").ifPresent(bone -> bone.setHidden(!block.wall && top));
        model.getBone("top").ifPresent(bone -> bone.setHidden(!block.wall && !top));
        super.preRender(poses, torch, model, buffers, buffer, rerender, partialTick, light, overlay, color);
    }
}

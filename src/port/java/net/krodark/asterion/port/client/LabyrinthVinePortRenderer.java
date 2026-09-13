package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/** Correct up/down geometry and terminal bulb visibility for ancient vines. */
public final class LabyrinthVinePortRenderer extends SimpleGeoBlockRenderer<LabyrinthVineBlockEntity> {
    public LabyrinthVinePortRenderer() {
        super(entity -> Asterion.id(entity.getBlockState().getValue(LabyrinthVineBlock.FACING)
                        == net.minecraft.core.Direction.UP ? "block/labyrinth_vine_up" : "block/labyrinth_vine"),
                ignored -> Asterion.id("textures/block/labyrinth_vine.png"),
                ignored -> Asterion.id("block/labyrinth_vine"));
    }

    @Override
    public void preRender(PoseStack poses, LabyrinthVineBlockEntity vine, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                          float partialTick, int light, int overlay, int color) {
        model.getBone("bulb").ifPresent(bone -> bone.setHidden(!vine.isEnd()));
        super.preRender(poses, vine, model, buffers, buffer, reRender, partialTick,
                light, overlay, color);
    }
}

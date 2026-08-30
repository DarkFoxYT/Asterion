package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Two-state vine with an emissive end bulb, preserving the authored downward pose. */
public final class LabyrinthVineGeoRenderer
        extends GeoBlockRenderer<LabyrinthVineBlockEntity, BlockEntityRenderState> {
    public LabyrinthVineGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new LabyrinthVineGeoModel());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "glow",
                Asterion.id("textures/block/labyrinth_vine.png")) {
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true);
            }

            @Override protected float surfaceBrightness(BlockEntityRenderState state) {
                return AsterionEmissiveConfig.vineGlowStrength();
            }
        });
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass,
                                          BoneSnapshots snapshots) {
        boolean end = pass.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true);
        snapshots.ifPresent("bulb", snapshot -> {
            snapshot.skipRender(!end);
            snapshot.skipChildrenRender(!end);
        });
        Direction facing = pass.getOrDefaultGeckolibData(
                LabyrinthVineGeoModel.FACING, Direction.DOWN);
        snapshots.ifPresent("full", snapshot -> {
            snapshot.setRotation(facing == Direction.UP ? (float)Math.PI : 0.0F,
                    0.0F, 0.0F);
        });
    }

    @Override
    public void addRenderData(LabyrinthVineBlockEntity vine, Void relatedObject,
                              BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(LabyrinthVineGeoModel.END, vine.isEnd());
        state.addGeckolibData(LabyrinthVineGeoModel.FACING,
                vine.getBlockState().getValue(LabyrinthVineBlock.FACING));
    }
}

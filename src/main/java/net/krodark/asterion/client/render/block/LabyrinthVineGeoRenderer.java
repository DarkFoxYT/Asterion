package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** Non-emissive vine renderer with all six directions pivoting around block center. */
public final class LabyrinthVineGeoRenderer
        extends GeoBlockRenderer<LabyrinthVineBlockEntity, BlockEntityRenderState> {
    public LabyrinthVineGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new LabyrinthVineGeoModel());
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
            float x = 0.0F;
            float z = 0.0F;
            switch (facing) {
                // The authored model points down. DOWN therefore remains the clean,
                // rotation-free reference pose and every other state is absolute.
                case UP -> x = (float)Math.PI;
                case NORTH -> x = (float)Math.PI * 0.5F;
                case SOUTH -> x = -(float)Math.PI * 0.5F;
                case EAST -> z = (float)Math.PI * 0.5F;
                case WEST -> z = -(float)Math.PI * 0.5F;
                default -> { }
            }
            snapshot.setRotation(x, 0.0F, z);
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

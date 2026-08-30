package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.krodark.asterion.client.light.AsterionEmissiveBuffer;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.geckolib.constant.DataTickets;
import com.geckolib.cache.model.GeoBone;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class LabyrinthVineGeoRenderer
        extends GeoBlockRenderer<LabyrinthVineBlockEntity, BlockEntityRenderState> {
    private static final Identifier TEXTURE = Asterion.id("textures/block/labyrinth_vine.png");
    private static final String EMISSIVE_BONE = "glow";

    public LabyrinthVineGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new LabyrinthVineGeoModel());
        // Only the authored glow child receives the emissive pass; the bone bulb remains
        // normally textured and bone2 is never hidden or transformed here.
        withRenderLayer(new CustomBoneTextureGeoLayer<>(this, EMISSIVE_BONE, TEXTURE) {
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true);
            }
            @Override protected RenderType getRenderType(BlockEntityRenderState state, Identifier texture) {
                return AsterionEmissiveBuffer.renderType(texture);
            }

            @Override
            protected void renderBone(RenderPassInfo<BlockEntityRenderState> pass, GeoBone bone,
                                      SubmitNodeCollector renderTasks) {
                int original = pass.renderState().getOrDefaultGeckolibData(
                        DataTickets.RENDER_COLOR, 0xFFFFFFFF);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR, 0xFFFF6A20);
                super.renderBone(pass, bone, renderTasks);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR, original);
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
                    Vec3.atCenterOf(vine.getBlockPos()).add(direction.scale(0.42D)),
                    1.0F, 0.48F, 0.08F, 1.75F, 8.0F, false);
        } else LedAmneticLight.removeItemGlowLight(vine);
    }
}

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

/** Uses each variant's authored pose; only the end segment's glow bone is emissive. */
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

            @Override protected boolean enhancedSurface(BlockEntityRenderState state) { return true; }

            @Override protected void renderBone(RenderPassInfo<BlockEntityRenderState> pass,
                    com.geckolib.cache.model.GeoBone bone, net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                // Never include the head shell or inherit emissiveness along the parent hierarchy.
                if (bone.name().equals("glow")) super.renderBone(pass, bone, tasks);
            }

            @Override protected net.minecraft.resources.Identifier amneticEmissionMesh(BlockEntityRenderState state) {
                return getGeoModel().getModelResource(state);
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
        // The inner core has its own full-bright pass. Drawing it here as well creates competing
        // shaded/emissive surfaces, particularly noticeable on the rotated hanging variant.
        snapshots.ifPresent("glow", snapshot -> snapshot.skipRender(true));
        snapshots.ifPresent("head", snapshot -> snapshot.skipRender(!end));
        // The upright asset already includes its root rotation and pivot.
        // Overriding "full" here would flip it again and misalign the bulb with the stem.
    }

    @Override
    public void addRenderData(LabyrinthVineBlockEntity vine, Void relatedObject,
                              BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(LabyrinthVineGeoModel.END, vine.isEnd());
        state.addGeckolibData(LabyrinthVineGeoModel.FACING,
                vine.getBlockState().getValue(LabyrinthVineBlock.FACING));
    }
}

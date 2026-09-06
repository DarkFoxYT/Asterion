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

 
public final class LabyrinthVineGeoRenderer
        extends GeoBlockRenderer<LabyrinthVineBlockEntity, BlockEntityRenderState> {
    private final java.util.Map<com.geckolib.cache.model.BakedGeoModel, StaticVineMesh[]> meshes =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();

    @Override public void submitRenderTasks(RenderPassInfo<BlockEntityRenderState> pass,
            net.minecraft.client.renderer.OrderedSubmitNodeCollector tasks,
            net.minecraft.client.renderer.rendertype.RenderType type) {
        if (type == null) return;
        if (pass.model().isMissingno()) { super.submitRenderTasks(pass, tasks, type); return; }
        var variants = meshes.computeIfAbsent(pass.model(), model -> new StaticVineMesh[2]);
        int variant = pass.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true) ? 1 : 0;
        if (variants[variant] == null) {
            variants[variant] = StaticVineMesh.bake(pass);
            if (Boolean.getBoolean("asterion.verifyStaticVines")) variants[variant].verify(pass);
        }
        var mesh = variants[variant];
        int color = pass.renderColor(), light = pass.packedLight(), overlay = pass.packedOverlay();
        tasks.submitCustomGeometry(pass.poseStack(), type,
                (pose, out) -> mesh.render(pose, out, color, light, overlay));
    }

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
         
         
        snapshots.ifPresent("glow", snapshot -> snapshot.skipRender(true));
        snapshots.ifPresent("head", snapshot -> snapshot.skipRender(!end));
         
         
    }

    @Override
    public void addRenderData(LabyrinthVineBlockEntity vine, Void relatedObject,
                              BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(LabyrinthVineGeoModel.END, vine.isEnd());
        state.addGeckolibData(LabyrinthVineGeoModel.FACING,
                vine.getBlockState().getValue(LabyrinthVineBlock.FACING));
    }
}

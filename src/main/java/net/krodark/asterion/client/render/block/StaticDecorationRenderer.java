package net.krodark.asterion.client.render.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Only for unanimated decorations without render layers or per-instance geometry. */
abstract class StaticDecorationRenderer<T extends BlockEntity & GeoBlockEntity>
        extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private static final DataTicket<BlockState> VARIANT = DataTickets.create("asterion_static_decoration", BlockState.class);
    // Model identity changes on resource reload; old meshes must not keep it alive.
    private final Map<BakedGeoModel, Map<BlockState, Draw>> cache =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private final Matrix4f inverse = new Matrix4f();
    private boolean capturing;

    StaticDecorationRenderer(BlockEntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override public int getViewDistance() { return AsterionConfig.INSTANCE.decorationRenderDistance; }

    private Map<BlockState, Draw> variants(BlockEntityRenderState state) {
        var model = getGeoModel().getBakedModel(getGeoModel().getModelResource(state));
        return cache.computeIfAbsent(model, ignored -> new HashMap<>());
    }

    @Override public void extractRenderState(T block, BlockEntityRenderState state, float partial,
            Vec3 camera, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(block, state, breaking);
        addRenderData(block, null, state, partial);
        state.addGeckolibData(VARIANT, block.getBlockState());
        if (breaking != null || !variants(state).containsKey(block.getBlockState())) {
            super.extractRenderState(block, state, partial, camera, breaking);
            state.addGeckolibData(VARIANT, block.getBlockState());
        }
    }

    @Override public void submit(BlockEntityRenderState state, PoseStack poses,
            SubmitNodeCollector tasks, CameraRenderState camera) {
        Draw draw = variants(state).get(state.getGeckolibData(VARIANT));
        if (draw == null || state.breakProgress != null) {
            inverse.set(poses.last().pose()).invert();
            capturing = state.breakProgress == null;
            try { super.submit(state, poses, tasks, camera); }
            finally { capturing = false; }
            return;
        }
        int light = state.lightCoords;
        poses.pushPose();
        poses.mulPose(draw.transform);
        tasks.submitCustomGeometry(poses, draw.type,
                (pose, out) -> draw.mesh.render(pose, out, draw.color, light, draw.overlay));
        poses.popPose();
    }

    @Override public void submitRenderTasks(RenderPassInfo<BlockEntityRenderState> pass,
            OrderedSubmitNodeCollector tasks, RenderType type) {
        if (type == null || pass.model().isMissingno() || !capturing) {
            super.submitRenderTasks(pass, tasks, type);
            return;
        }
        StaticVineMesh mesh = StaticVineMesh.bake(pass);
        int color = pass.renderColor(), light = pass.packedLight(), overlay = pass.packedOverlay();
        variants(pass.renderState()).put(pass.renderState().getGeckolibData(VARIANT),
                new Draw(mesh, type, new Matrix4f(inverse).mul(pass.poseStack().last().pose()), color, overlay));
        tasks.submitCustomGeometry(pass.poseStack(), type,
                (pose, out) -> mesh.render(pose, out, color, light, overlay));
    }

    private record Draw(StaticVineMesh mesh, RenderType type, Matrix4f transform, int color, int overlay) { }
}

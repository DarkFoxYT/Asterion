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

    private final java.util.Map<com.geckolib.cache.model.BakedGeoModel, CachedVine[]> draws =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private final org.joml.Matrix4f captureInverse = new org.joml.Matrix4f();
    private CachedVine capturing;
    private boolean captureActive;

    private static final class CachedVine {
        StaticVineMesh body;
        net.minecraft.client.renderer.rendertype.RenderType type;
        int color, overlay;
        org.joml.Matrix4f bodyPose;
        net.krodark.asterion.client.light.EmissiveBoneMesh glow;
        org.joml.Matrix4f glowPose;
        net.minecraft.resources.Identifier model;
        boolean complete;
    }

    private CachedVine cached(BlockEntityRenderState state) {
        var model = getGeoModel().getBakedModel(getGeoModel().getModelResource(state));
        var variants = draws.get(model);
        return variants == null ? null : variants[state.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true) ? 1 : 0];
    }

    @Override public void extractRenderState(LabyrinthVineBlockEntity vine, BlockEntityRenderState state,
            float partialTick, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(vine, state, breaking);
        addRenderData(vine, null, state, partialTick);
        var cached = cached(state);
        if (cached == null || !cached.complete || breaking != null)
            super.extractRenderState(vine, state, partialTick, camera, breaking);
    }

    @Override public void submit(BlockEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.SubmitNodeCollector tasks,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        var cached = cached(state);
        if (cached == null || !cached.complete || state.breakProgress != null) {
            captureInverse.set(poses.last().pose()).invert();
            captureActive = state.breakProgress == null;
            capturing = null;
            try {
                super.submit(state, poses, tasks, camera);
                if (capturing != null) capturing.complete = !state.getOrDefaultGeckolibData(LabyrinthVineGeoModel.END, true)
                        || capturing.glow != null;
            } finally { captureActive = false; capturing = null; }
            return;
        }
        int light = state.lightCoords;
        poses.pushPose();
        poses.mulPose(cached.bodyPose);
        tasks.submitCustomGeometry(poses, cached.type,
                (pose, out) -> cached.body.render(pose, out, cached.color, light, cached.overlay));
        poses.popPose();
        if (cached.glow != null) {
            int color = net.krodark.asterion.client.light.EmissiveBoneMesh.dimColor(-1,
                    AsterionEmissiveConfig.vineGlowStrength());
            poses.pushPose();
            poses.mulPose(cached.glowPose);
            var texture = Asterion.id("textures/block/labyrinth_vine.png");
            tasks.submitCustomGeometry(poses, net.krodark.asterion.client.light.AsterionEmissiveBuffer.renderType(texture),
                    (pose, out) -> {
                        cached.glow.render(pose, out, color, 1, 1);
                        net.krodark.asterion.client.light.AmneticBoneEmission.submit(cached.model, cached.glow, texture,
                                pose.pose(), color, 1, 1, 1, true);
                    });
            poses.popPose();
        }
    }

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
        if (captureActive) {
            var cached = new CachedVine();
            cached.body = mesh;
            cached.type = type;
            cached.color = color;
            cached.overlay = overlay;
            cached.bodyPose = new org.joml.Matrix4f(captureInverse).mul(pass.poseStack().last().pose());
            cached.model = getGeoModel().getModelResource(pass.renderState());
            draws.computeIfAbsent(pass.model(), ignored -> new CachedVine[2])[variant] = cached;
            capturing = cached;
        }
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
                 
                if (bone.name().equals("glow")) {
                    if (captureActive && capturing != null && bone instanceof com.geckolib.cache.model.cuboid.CuboidGeoBone cuboid) {
                        var stack = pass.poseStack();
                        stack.pushPose();
                        bone.translateAwayFromPivotPoint(stack);
                        capturing.glowPose = new org.joml.Matrix4f(captureInverse).mul(stack.last().pose());
                        capturing.glow = net.krodark.asterion.client.light.EmissiveBoneMesh.of(cuboid);
                        stack.popPose();
                    }
                    super.renderBone(pass, bone, tasks);
                }
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

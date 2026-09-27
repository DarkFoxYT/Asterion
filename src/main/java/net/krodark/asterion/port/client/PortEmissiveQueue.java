package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Defers Geo emissive geometry until every normal entity and block-entity
 * surface has populated the main depth buffer.
 */
final class PortEmissiveQueue {
    private static final List<Draw> POOL = new ArrayList<>();
    private static final PoseStack POSES = new PoseStack();
    private static int count;
    private static boolean collecting;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final java.util.Map<GeoBone, net.minecraft.world.phys.AABB> BOUNDS = new java.util.WeakHashMap<>();
    private static final Matrix4f CLIP = new Matrix4f();
    private static final org.joml.FrustumIntersection FRUSTUM = new org.joml.FrustumIntersection();
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    private static final Matrix4f WORLD_MODEL_VIEW = new Matrix4f();
    private static boolean initialized;

    private PortEmissiveQueue() {}

    static void initialize() {
        if (initialized) return;
        initialized = true;
        PortEmissiveBuffer.initialize();
        com.meekdev.amnetic.client.emissive.EmissiveSources.register(
                net.krodark.asterion.Asterion.id("gecko_emission"), context -> {
                    if (!PortEmissiveBuffer.bloomEnabled()) return;
                    var source = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
                    if (count == 0) return;
                    Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
                    VertexSorting previousSorting = RenderSystem.getVertexSorting();
                    var modelView = RenderSystem.getModelViewStack();
                    //? if >=1.20.5 {
                    modelView.pushMatrix();
                    modelView.set(WORLD_MODEL_VIEW);
                    //?} else {
                    /*modelView.pushPose();
                    modelView.last().pose().set(WORLD_MODEL_VIEW);*/
                    //?}
                    RenderSystem.setProjectionMatrix(WORLD_PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
                    RenderSystem.applyModelViewMatrix();
                    try {
                        for (int i = 0; i < count; i++) POOL.get(i).render(source);
                        source.endBatch();
                    } finally {
                        //? if >=1.20.5 {
                        modelView.popMatrix();
                        //?} else {
                        /*modelView.popPose();*/
                        //?}
                        RenderSystem.applyModelViewMatrix();
                        RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
                    }
                });
        WorldRenderEvents.START.register(context -> {
            tick(net.minecraft.client.Minecraft.getInstance());
            resetFrame();
            collecting = true;
        });
        WorldRenderEvents.AFTER_SETUP.register(context -> PortPointLights.cull(context.frustum()));
        // This fires after both entities and block entities. It also runs when
        // there is no outline, unlike BLOCK_OUTLINE itself.
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) -> {
            collecting = false;
            flush(context.consumers());
            return true;
        });
    }

    private static void flush(MultiBufferSource buffers) {
        if (count == 0 || !(buffers instanceof MultiBufferSource.BufferSource source)) return;

        // This is intentionally a single stage-level flush. Flushing from
        // inside a Geo render recursively disturbs the active upload state.
        source.endBatch();
        for (int i = 0; i < count; i++) POOL.get(i).render(source);
        source.endBatch();
    }

    static void submit(GeoRenderer<?> renderer, GeoBone bone, PoseStack.Pose transform,
                       ResourceLocation texture, int tint, int overlay) {
        if (!collecting || world == null || bone.getCubes().isEmpty()) return;
        var bounds = BOUNDS.computeIfAbsent(bone, PortEmissiveQueue::bounds);
        CLIP.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(transform.pose());
        if (!visible(CLIP, bounds)) return;
        if (count == 0) {
            WORLD_PROJECTION.set(RenderSystem.getProjectionMatrix());
            WORLD_MODEL_VIEW.set(RenderSystem.getModelViewMatrix());
        }
        Draw draw;
        if (count < POOL.size()) draw = POOL.get(count);
        else {
            draw = new Draw();
            POOL.add(draw);
        }
        draw.capture(renderer, bone, transform, texture, tint, overlay);
        count++;
    }

    static void tick(net.minecraft.client.Minecraft client) {
        if (world == client.level) return;
        resetFrame();
        POOL.clear(); BOUNDS.clear(); collecting = false;
        world = client.level;
    }

    private static void resetFrame() {
        for (int i = 0; i < count; i++) POOL.get(i).release();
        count = 0;
        // Retain a modest allocation cache, not the largest scene ever visited.
        if (POOL.size() > 2048) POOL.subList(2048, POOL.size()).clear();
    }

    static boolean visible(Matrix4f clip, net.minecraft.world.phys.AABB bounds) {
        return FRUSTUM.set(clip).testAab((float)bounds.minX, (float)bounds.minY, (float)bounds.minZ,
                (float)bounds.maxX, (float)bounds.maxY, (float)bounds.maxZ);
    }

    private static net.minecraft.world.phys.AABB bounds(GeoBone bone) {
        PoseStack poses = new PoseStack();
        org.joml.Vector3f point = new org.joml.Vector3f();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for (var cube : bone.getCubes()) {
            poses.pushPose();
            //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poses,cube);
            software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundCube(poses,cube);
            software.bernie.geckolib.util.RenderUtil.translateAwayFromPivotPoint(poses,cube);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(poses,cube);
            software.bernie.geckolib.util.RenderUtils.rotateMatrixAroundCube(poses,cube);
            software.bernie.geckolib.util.RenderUtils.translateAwayFromPivotPoint(poses,cube);*/
            //?}
            for (var quad : cube.quads()) if (quad != null) for (var vertex : quad.vertices()) {
                poses.last().pose().transformPosition(vertex.position(),point);
                minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);
                maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
            }
            poses.popPose();
        }
        return new net.minecraft.world.phys.AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(.25);
    }

    private static final class Draw {
        private final Matrix4f pose = new Matrix4f();
        private final Matrix3f normal = new Matrix3f();
        private GeoRenderer<?> renderer;
        private GeoBone bone;
        private ResourceLocation texture;
        private int tint;
        private int overlay;

        private void capture(GeoRenderer<?> renderer, GeoBone bone, PoseStack.Pose transform,
                             ResourceLocation texture, int tint, int overlay) {
            this.renderer = renderer;
            this.bone = bone;
            this.pose.set(transform.pose());
            this.normal.set(transform.normal());
            this.texture = texture;
            this.tint = tint;
            this.overlay = overlay;
        }

        private void release() { renderer = null; bone = null; texture = null; }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void render(MultiBufferSource.BufferSource buffers) {
            RenderType emission = PortEmissiveBuffer.renderType(texture);
            VertexConsumer out = buffers.getBuffer(emission);
            POSES.last().pose().set(pose);
            POSES.last().normal().set(normal);
            // Baked GeoBones are shared between animatable instances. A later
            // non-emissive instance may have hidden this same bone after this
            // draw was captured, so restore the captured visible state locally.
            boolean hidden = bone.isHidden();
            boolean childrenHidden = bone.isHidingChildren();
            bone.setHidden(false);

//? if >=1.20.5 {
((GeoRenderer)renderer).renderCubesOfBone(POSES, bone, out,
                    LightTexture.FULL_BRIGHT, overlay, tint);
//?} else {
/*((GeoRenderer)renderer).renderCubesOfBone(POSES, bone, out,
                    LightTexture.FULL_BRIGHT, overlay, ((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, ((tint >>> 24) & 255) / 255F);*/
//?}

            bone.setHidden(hidden);
            bone.setChildrenHidden(childrenHidden);
        }
    }
}

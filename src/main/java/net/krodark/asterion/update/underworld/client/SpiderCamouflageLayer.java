package net.krodark.asterion.update.underworld.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.function.BiConsumer;

/** Re-textures each body/leg segment with the block material directly beneath that part. */
public final class SpiderCamouflageLayer extends GeoRenderLayer<LimboSpiderEntity, Void, EntityRenderState> {
    private static final DataTicket<Boolean> CAMOUFLAGED = DataTickets.create("asterion_spider_block_camouflage", Boolean.class);
    private static final List<String> BONES = List.of("body", "abnomen", "abdomen", "head", "mask", "jaw", "sectionleft",
            "sectionright", "webmaker", "leftlegfront", "leftlegfrontmid",
            "leftlegback", "leftlegbackmid", "rightlegfront", "rightlegfrontmid", "rightlegback",
            "rightlegbackmid", "leftlegfrontish", "leftlegfrontishmid", "leftlegbackish",
            "leftlegbackishmid", "rightlegfrontish", "rightlegfrontishmid", "rightlegbackish", "rightlegbackishmid");

    public SpiderCamouflageLayer(LimboSpiderRenderer renderer) { super(renderer); }

    @Override public void addRenderData(LimboSpiderEntity spider, Void unused, EntityRenderState state, float partialTick) {
        state.addGeckolibData(CAMOUFLAGED, spider.camouflaged() && spider.hasCamouflageSupport());
    }

    @Override public void preRender(RenderPassInfo<EntityRenderState> pass, SubmitNodeCollector tasks) {
        if (!pass.willRender() || !pass.getOrDefaultGeckolibData(CAMOUFLAGED, false)) return;
        pass.addBoneUpdater((ignored, snapshots) -> hideOriginalBones(snapshots));
    }

    private static void hideOriginalBones(BoneSnapshots snapshots) {
        for (String name : BONES) snapshots.get(name).ifPresent(snapshot -> {
            if (snapshot.getBone() instanceof CuboidGeoBone) {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(false);
            }
        });
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
                                           BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        if (!pass.willRender() || !pass.getOrDefaultGeckolibData(CAMOUFLAGED, false)) return;
        for (String name : BONES) pass.model().getBone(name).filter(CuboidGeoBone.class::isInstance)
                .ifPresent(bone -> consumer.accept(bone, this::renderSegment));
    }

    private void renderSegment(RenderPassInfo<EntityRenderState> pass, GeoBone bone, SubmitNodeCollector tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(bone instanceof CuboidGeoBone cuboid)) return;
        Vector3f relative = pass.poseStack().last().pose().transformPosition(new Vector3f());
        Vec3 origin = new Vec3(relative.x, relative.y, relative.z).add(pass.cameraState().pos);
        // The model rotates onto walls and ceilings; use the entity's synced support direction via render data.
        Direction towardSurface = pass.getOrDefaultGeckolibData(SURFACE, Direction.DOWN);
        BlockState under = blockUnder(client, origin, towardSurface);
        TextureAtlasSprite sprite = under == null ? null
                : client.getModelManager().getBlockStateModelSet().getParticleMaterial(under).sprite();
        var texture = sprite == null ? renderer.getTextureLocation(pass.renderState()) : sprite.atlasLocation();
        tasks.submitCustomGeometry(pass.poseStack(), RenderTypes.entityCutout(texture, false), (pose, out) -> {
            PoseStack stack = pass.poseStack();
            stack.pushPose();
            stack.last().set(pose);
            bone.translateAwayFromPivotPoint(stack);
            for (GeoCube cube : cuboid.cubes) {
                stack.pushPose();
                cube.translateToPivotPoint(stack);
                cube.rotate(stack);
                cube.translateAwayFromPivotPoint(stack);
                Matrix3f normalMatrix = stack.last().normal();
                Matrix4f matrix = new Matrix4f(stack.last().pose());
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    Vector3f normal = normalMatrix.transform(new Vector3f(quad.normalVec()));
                    com.geckolib.util.RenderUtil.fixInvertedFlatCube(cube, normal);
                    // Model UVs are normalized against the spider skin. Remap each
                    // face to the sampled block sprite, not a tiny patch of that sprite.
                    float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
                    float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
                    for (GeoVertex vertex : quad.vertices()) {
                        minU = Math.min(minU, vertex.texU()); maxU = Math.max(maxU, vertex.texU());
                        minV = Math.min(minV, vertex.texV()); maxV = Math.max(maxV, vertex.texV());
                    }
                    for (GeoVertex vertex : quad.vertices()) {
                        Vector4f point = matrix.transform(new Vector4f(vertex.posX(), vertex.posY(), vertex.posZ(), 1));
                        float u = (vertex.texU() - minU) / Math.max(.000001F, maxU - minU);
                        float v = (vertex.texV() - minV) / Math.max(.000001F, maxV - minV);
                        out.addVertex(point.x(), point.y(), point.z(), pass.renderColor(),
                                sprite == null ? vertex.texU() : sprite.getU(u), sprite == null ? vertex.texV() : sprite.getV(v),
                                pass.packedOverlay(), pass.packedLight(), normal.x(), normal.y(), normal.z());
                    }
                }
                stack.popPose();
            }
            stack.popPose();
        });
    }

    private static final DataTicket<Direction> SURFACE = DataTickets.create("asterion_spider_camouflage_surface", Direction.class);

    private static BlockState blockUnder(Minecraft client, Vec3 origin, Direction towardSurface) {
        var level = client.level;
        Vec3 ray = towardSurface.getUnitVec3();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double distance = .15; distance <= 4; distance += .35) {
            Vec3 point = origin.add(ray.scale(distance));
            pos.set(point.x, point.y, point.z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty())
                return state;
        }
        return null;
    }

}

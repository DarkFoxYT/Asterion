package net.krodark.asterion.client.render.entity;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.client.MinotaurBodyPicking;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Reuses the evaluated animation instead of ticking invisible hitbox entities. */
public final class MinotaurBodyLayer extends GeoRenderLayer<MinotaurEntity, Void, EntityRenderState> {
    private static final DataTicket<MinotaurBodyPicking.Body> BODY = DataTickets.create("asterion_body_pick", MinotaurBodyPicking.Body.class);
    private static final Map<CuboidGeoBone, List<AABB>> SHAPES = new com.google.common.collect.MapMaker().weakKeys().makeMap();

    public MinotaurBodyLayer(MinotaurGeoRenderer renderer) { super(renderer); }

    @Override public void addRenderData(MinotaurEntity boss, Void ignored, EntityRenderState state, float partial) {
        state.addGeckolibData(BODY, MinotaurBodyPicking.begin(boss));
    }

    @Override public void submitRenderTask(RenderPassInfo<EntityRenderState> pass,
            net.minecraft.client.renderer.SubmitNodeCollector tasks) {
        // Publish after all bone callbacks, so picking cannot observe a half-built frame.
        MinotaurBodyPicking.publish(pass.getOrDefaultGeckolibData(BODY, (MinotaurBodyPicking.Body)null));
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        var body = pass.getOrDefaultGeckolibData(BODY, (MinotaurBodyPicking.Body)null);
        if (body == null || !pass.willRender() || pass.renderState().isInvisible) return;
        for (var bone : pass.model().boneLookup().get().values()) {
            if (!(bone instanceof CuboidGeoBone cubes) || cubes.cubes.length == 0) continue;
            consumer.accept(bone, (posed, ignored, tasks) -> {
                if (bone.frameSnapshot != null && bone.frameSnapshot.isHidden()) return;
                var poses = posed.poseStack();
                poses.pushPose();
                bone.translateAwayFromPivotPoint(poses);
                body.add(new Matrix4f(poses.last().pose()).invert(), posed.cameraState().pos,
                        SHAPES.computeIfAbsent(cubes, MinotaurBodyLayer::shapes), part(bone).ordinal());
                poses.popPose();
            });
        }
    }

    static net.krodark.asterion.entity.MinotaurRemains part(GeoBone bone) {
        for (GeoBone parent = bone; parent != null; parent = parent.parent()) {
            var part = net.krodark.asterion.entity.MinotaurRemains.root(parent.name());
            if (part != null) return part;
        }
        return net.krodark.asterion.entity.MinotaurRemains.TORSO;
    }

    private static List<AABB> shapes(CuboidGeoBone bone) {
        var result = new ArrayList<AABB>();
        var poses = new PoseStack();
        var vertex = new Vector3f();
        for (var cube : bone.cubes) {
            poses.pushPose();
            cube.translateToPivotPoint(poses);
            cube.rotate(poses);
            cube.translateAwayFromPivotPoint(poses);
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var v : quad.vertices()) {
                    poses.last().pose().transformPosition(v.posX(), v.posY(), v.posZ(), vertex);
                    minX = Math.min(minX, vertex.x); minY = Math.min(minY, vertex.y); minZ = Math.min(minZ, vertex.z);
                    maxX = Math.max(maxX, vertex.x); maxY = Math.max(maxY, vertex.y); maxZ = Math.max(maxZ, vertex.z);
                }
            }
            poses.popPose();
            if (Double.isFinite(minX)) result.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(.035));
        }
        return List.copyOf(result);
    }
}

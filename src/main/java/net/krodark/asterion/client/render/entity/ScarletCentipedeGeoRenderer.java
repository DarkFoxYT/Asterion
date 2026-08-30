package net.krodark.asterion.client.render.entity;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.krodark.asterion.entity.CentipedeCollision;

public final class ScarletCentipedeGeoRenderer
        extends GeoEntityRenderer<ScarletCentipedeEntity, EntityRenderState> {
    private static final DataTicket<ProceduralCentipedeChain.Pose> CHAIN_POSE = DataTickets.create(
            "asterion_centipede_chain_pose", ProceduralCentipedeChain.Pose.class);

    public ScarletCentipedeGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new ScarletCentipedeGeoModel());
        shadowRadius = 1.44F;
    }

    @Override
    protected AABB getBoundingBoxForCulling(ScarletCentipedeEntity entity) {
        AABB bounds = entity.getBoundingBox().inflate(2);
        for (int i = 0; i < entity.chainSegmentCount(); i++) {
            var pose = entity.chainPose(i, 1);
            bounds = bounds.minmax(CentipedeCollision.volume(pose.position(), new Vec3(2, 2, 2)));
        }
        return bounds;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<EntityRenderState> pass, float width, float height) {
        super.scaleModelForRender(pass, width * CentipedeFrame.MODEL_SCALE, height * CentipedeFrame.MODEL_SCALE);
    }

    @Override
    public void addRenderData(ScarletCentipedeEntity centipede, Void relatedObject,
                              EntityRenderState state, float partialTick) {
        state.addGeckolibData(CHAIN_POSE, ProceduralCentipedeChain.extract(
                centipede, partialTick, new Vec3(state.x, state.y, state.z)));
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<EntityRenderState> pass) {
        // Every anchor already has a world frame. Do not add vanilla body yaw, death roll,
        // shaking, or the global Y bob here -- there is no matching root transform to cancel.
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        ProceduralCentipedeChain.Pose pose = pass.getGeckolibData(CHAIN_POSE);
        if (pose != null) pose.apply(bones);
    }
}

package net.krodark.asterion.client.render.entity;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ScarletCentipedeGeoRenderer
        extends GeoEntityRenderer<ScarletCentipedeEntity, EntityRenderState> {
    private static final DataTicket<Float> ROT_X = DataTickets.create("asterion_centipede_rot_x", Float.class);
    private static final DataTicket<Float> ROT_Y = DataTickets.create("asterion_centipede_rot_y", Float.class);
    private static final DataTicket<Float> ROT_Z = DataTickets.create("asterion_centipede_rot_z", Float.class);
    private static final DataTicket<Float> ROT_W = DataTickets.create("asterion_centipede_rot_w", Float.class);
    private static final DataTicket<ProceduralCentipedeChain.Pose> CHAIN_POSE = DataTickets.create(
            "asterion_centipede_chain_pose", ProceduralCentipedeChain.Pose.class);
    private final Map<UUID, Quaternionf> poses = new WeakHashMap<>();
    private final Map<UUID, ProceduralCentipedeChain> chains = new WeakHashMap<>();

    public ScarletCentipedeGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new ScarletCentipedeGeoModel());
        shadowRadius = 0.72F;
    }

    @Override
    public void addRenderData(ScarletCentipedeEntity centipede, Void relatedObject,
                              EntityRenderState state, float partialTick) {
        float renderYaw = calculateYRot(centipede, 0.0F, partialTick);
        Quaternionf target = SurfaceOrientation.relativeToRenderYaw(
                centipede.attachmentNormal(), centipede.surfaceForward(), renderYaw);
        Quaternionf pose = poses.computeIfAbsent(centipede.getUUID(), ignored -> new Quaternionf());
        float frameTicks = Math.max(0.05F, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        pose.slerp(target, 1.0F - (float)Math.pow(0.76D, frameTicks)).normalize();
        state.addGeckolibData(ROT_X, pose.x);
        state.addGeckolibData(ROT_Y, pose.y);
        state.addGeckolibData(ROT_Z, pose.z);
        state.addGeckolibData(ROT_W, pose.w);
        ProceduralCentipedeChain chain = chains.computeIfAbsent(centipede.getUUID(),
                ignored -> new ProceduralCentipedeChain());
        state.addGeckolibData(CHAIN_POSE, chain.update(centipede, partialTick, frameTicks));
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<EntityRenderState> pass) {
        super.adjustRenderPose(pass);
        pass.poseStack().mulPose(new Quaternionf(
                pass.getOrDefaultGeckolibData(ROT_X, 0.0F),
                pass.getOrDefaultGeckolibData(ROT_Y, 0.0F),
                pass.getOrDefaultGeckolibData(ROT_Z, 0.0F),
                pass.getOrDefaultGeckolibData(ROT_W, 1.0F)).normalize());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        ProceduralCentipedeChain.Pose chainPose = pass.getGeckolibData(CHAIN_POSE);
        if (chainPose != null) chainPose.apply(bones);
    }
}

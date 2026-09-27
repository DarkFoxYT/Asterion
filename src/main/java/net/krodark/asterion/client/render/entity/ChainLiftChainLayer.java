package net.krodark.asterion.client.render.entity;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.function.BiConsumer;

public final class ChainLiftChainLayer extends GeoRenderLayer<ChainLiftEntity, Void, EntityRenderState> {
    private static final DataTicket<Vec3> CEILING = DataTickets.create("asterion_lift_ceiling", Vec3.class);
    public ChainLiftChainLayer(ChainLiftRenderer renderer) { super(renderer); }
    @Override public void addRenderData(ChainLiftEntity lift, Void unused, EntityRenderState state, float partial) {
        state.addGeckolibData(CEILING, new Vec3(lift.getX(), lift.ceiling(), lift.getZ()));
    }
    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass, BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        pass.model().getBone("chain").ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            Vector3f point = posed.poseStack().last().pose().transformPosition(new Vector3f());
            Vec3 start = new Vec3(point.x, point.y, point.z);
            Vec3 end = pass.renderState().getGeckolibData(CEILING).subtract(posed.cameraState().pos);
            if (end.y <= start.y) return;
            tasks.submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(Asterion.id("textures/block/mazesteel_chain.png")),
                    (pose, out) -> MinotaurChainLayer.draw(out, start, end, 0, posed.packedLight()));
        }));
    }
}

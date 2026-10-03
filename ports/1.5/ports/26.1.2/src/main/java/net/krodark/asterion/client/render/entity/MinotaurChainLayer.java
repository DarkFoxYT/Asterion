package net.krodark.asterion.client.render.entity;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.BiConsumer;

 
public final class MinotaurChainLayer extends GeoRenderLayer<MinotaurEntity, Void, EntityRenderState> {
    private static final DataTicket<Chain> CHAIN = DataTickets.create("asterion_minotaur_chain", Chain.class);
    private static final RenderType MATERIAL = RenderTypes.entityCutout(Asterion.id("textures/block/mazesteel_chain.png"));
    private record Chain(Vec3 target, float ticks, int arm, boolean held) {}

    public MinotaurChainLayer(MinotaurGeoRenderer renderer) { super(renderer); }

    @Override public void addRenderData(MinotaurEntity boss, Void ignored, EntityRenderState state, float partial) {
        if (!boss.isChainGrappleActive() && !boss.isPerformingGrab()) return;
        var target = boss.level().getEntity(boss.grabTargetEntityId());
        float ticks = boss.bossAttackAnimationTicks() + partial;
        boolean held = boss.heldPlayerId() >= 0;
        if (target != null && target.isAlive() && (held || ticks >= 8 && ticks < 36))
            state.addGeckolibData(CHAIN, new Chain(target.getPosition(partial)
                    .add(0, target.getBbHeight() * .55, 0), ticks, boss.reachArmSide(), held));
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        Chain chain = pass.renderState().getOrDefaultGeckolibData(CHAIN, (Chain)null);
        if (chain == null || !pass.willRender() || pass.renderState().isInvisible) return;
        String forearmAnchor = chain.arm >= 0 ? "hand_chainR" : "hand_chainL";
        String handFallback = chain.arm >= 0 ? "hand_itemR" : "hand_itemL";
        pass.model().getBone(forearmAnchor).or(() -> pass.model().getBone(handFallback))
                .ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            Vector3f hand = posed.poseStack().last().pose().transformPosition(new Vector3f());
            Vec3 start = new Vec3(hand.x, hand.y, hand.z);
            Vec3 target = chain.target.subtract(posed.cameraState().pos);
            float extension = chain.held ? 1 : Mth.clamp((chain.ticks - 12) / 7, 0, 1)
                    * Mth.clamp((36 - chain.ticks) / 9, 0, 1);
            Vec3 end = chain.held ? start.add(0, -.35, 0) : start.lerp(target, extension);
            double length = start.distanceTo(end);
            if (length < .05 || length > 40) return;
             
            double slack = Math.min(1.3, length * .09) * Mth.clamp(Math.abs(chain.ticks - 25) / 7, .06F, 1);
            int light = posed.packedLight();
            tasks.submitCustomGeometry(new PoseStack(), MATERIAL, (pose, out) -> draw(out, start, end, slack, light));
        }));
    }

    static void draw(VertexConsumer out, Vec3 start, Vec3 end, double slack, int light) {
        int links = Math.min(288, Math.max(2, Mth.ceil(start.distanceTo(end) / .25)));
        Vec3[] points = new Vec3[links + 1];
        for (int i = 0; i <= links; i++) {
            double t = i / (double)links;
            points[i] = start.lerp(end, t).add(0, -4 * slack * t * (1 - t), 0);
        }
        ChainGeometry.drawWeapon(new PoseStack().last(), out, points, Vec3.ZERO, light);
    }

}

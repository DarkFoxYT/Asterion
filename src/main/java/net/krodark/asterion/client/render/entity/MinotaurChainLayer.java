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

/** The block's crossed chain links, stretched from the evaluated hand to the hooked player's chest. */
public final class MinotaurChainLayer extends GeoRenderLayer<MinotaurEntity, Void, EntityRenderState> {
    private static final DataTicket<Chain> CHAIN = DataTickets.create("asterion_minotaur_chain", Chain.class);
    private static final RenderType MATERIAL = RenderTypes.entityCutout(Asterion.id("textures/block/mazesteel_chain.png"));
    private record Chain(Vec3 target, float ticks, int arm) {}

    public MinotaurChainLayer(MinotaurGeoRenderer renderer) { super(renderer); }

    @Override public void addRenderData(MinotaurEntity boss, Void ignored, EntityRenderState state, float partial) {
        if (!boss.isChainGrappleActive()) return;
        var target = boss.level().getEntity(boss.grabTargetEntityId());
        float ticks = boss.bossAttackAnimationTicks() + partial;
        if (target != null && target.isAlive() && ticks >= 12 && ticks < 36)
            state.addGeckolibData(CHAIN, new Chain(target.getPosition(partial)
                    .add(0, target.getBbHeight() * .55, 0), ticks, boss.reachArmSide()));
    }

    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,
            BiConsumer<GeoBone, PerBoneRender<EntityRenderState>> consumer) {
        Chain chain = pass.renderState().getOrDefaultGeckolibData(CHAIN, (Chain)null);
        if (chain == null || !pass.willRender() || pass.renderState().isInvisible) return;
        String forearmAnchor = chain.arm >= 0 ? "right_player_grip" : "left_player_grip";
        String handFallback = chain.arm >= 0 ? "hand_itemR" : "hand_itemL";
        pass.model().getBone(forearmAnchor).or(() -> pass.model().getBone(handFallback))
                .ifPresent(bone -> consumer.accept(bone, (posed, ignored, tasks) -> {
            Vector3f hand = posed.poseStack().last().pose().transformPosition(new Vector3f());
            Vec3 start = new Vec3(hand.x, hand.y, hand.z);
            Vec3 target = chain.target.subtract(posed.cameraState().pos);
            float extension = Mth.clamp((chain.ticks - 12) / 7, 0, 1)
                    * Mth.clamp((36 - chain.ticks) / 9, 0, 1);
            Vec3 end = start.lerp(target, extension);
            double length = start.distanceTo(end);
            if (length < .05 || length > 40) return;
            // Slack draws out, snaps taut on the one server-authoritative yank, then reels back in.
            double slack = Math.min(1.3, length * .09) * Mth.clamp(Math.abs(chain.ticks - 25) / 7, .06F, 1);
            int light = posed.packedLight();
            tasks.submitCustomGeometry(new PoseStack(), MATERIAL, (pose, out) -> draw(out, start, end, slack, light));
        }));
    }

    private static void draw(VertexConsumer out, Vec3 start, Vec3 end, double slack, int light) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 across = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 other = axis.cross(across).normalize();
        int links = Math.min(96, Math.max(1, Mth.ceil(start.distanceTo(end) / .45)));
        Vec3 a = start;
        for (int i = 1; i <= links; i++) {
            double t = i / (double)links;
            Vec3 b = start.lerp(end, t).add(0, -4 * slack * t * (1 - t), 0);
            // Same two UV islands as models/block/mazesteel_chain.json; no generic leash texture.
            quad(out, a, b, across.scale(.20), other, 0, .25F, light);
            quad(out, a, b, other.scale(.20), across, 4.25F / 16, 8.25F / 16, light);
            a = b;
        }
    }

    private static void quad(VertexConsumer out, Vec3 a, Vec3 b, Vec3 width, Vec3 normal, float u0, float u1, int light) {
        vertex(out, a.subtract(width), u0, 0, normal, light);
        vertex(out, a.add(width), u1, 0, normal, light);
        vertex(out, b.add(width), u1, .25F, normal, light);
        vertex(out, b.subtract(width), u0, .25F, normal, light);
    }

    private static void vertex(VertexConsumer out, Vec3 point, float u, float v, Vec3 normal, int light) {
        out.addVertex((float)point.x, (float)point.y, (float)point.z, -1, u, v,
                OverlayTexture.NO_OVERLAY, light, (float)normal.x, (float)normal.y, (float)normal.z);
    }
}

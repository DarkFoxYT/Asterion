package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlock;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Renders the authored five-block-wide crucible and its contextual temperature gauge. */
public final class CrucibleGaugeRenderer extends GeoBlockRenderer<CrucibleBlockEntity, BlockEntityRenderState> {
    private static final Identifier GAUGE = Asterion.id("textures/gui/temp_gauge_highres.png");
    private static final DataTicket<Float> TEMPERATURE = DataTickets.create("asterion_crucible_temperature", Float.class);
    private static final DataTicket<Boolean> GAUGE_VISIBLE = DataTickets.create("asterion_crucible_gauge_visible", Boolean.class);
    private float displayedTemperature;
    private boolean initialized;

    public CrucibleGaugeRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }

    @Override public void addRenderData(CrucibleBlockEntity crucible, Void related,
                                        BlockEntityRenderState state, float partialTick) {
        if (!initialized) {
            displayedTemperature = crucible.temperature();
            initialized = true;
        }
        displayedTemperature += (crucible.temperature() - displayedTemperature) * .14F;
        Minecraft client = Minecraft.getInstance();
        boolean visible = false;
        if (client.screen == null && client.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockState hitState = client.level == null ? null : client.level.getBlockState(hit.getBlockPos());
            visible = hitState != null && hitState.is(Asterion.CRUCIBLE)
                    && CrucibleBlock.root(hit.getBlockPos(), hitState).equals(crucible.getBlockPos());
        }
        state.addGeckolibData(TEMPERATURE, displayedTemperature);
        state.addGeckolibData(GAUGE_VISIBLE, visible);
    }

    @Override public void submit(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
                                 CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        if (!state.getOrDefaultGeckolibData(GAUGE_VISIBLE, false)) return;
        float shown = state.getOrDefaultGeckolibData(TEMPERATURE, 0F);
        for (Direction side : Direction.Plane.HORIZONTAL) submitGauge(poses, collector, side, shown);
    }

    private static void submitGauge(PoseStack poses, SubmitNodeCollector collector,
                                    Direction side, float temperature) {
        poses.pushPose();
        poses.translate(.5F + side.getStepX() * 2.511F, 2.45F,
                .5F + side.getStepZ() * 2.511F);
        float yaw = switch (side) {
            case NORTH -> 180F; case EAST -> 90F; case WEST -> -90F; default -> 0F;
        };
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        float height = .72F, width = height * (64F / 208F);
        collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(GAUGE, false),
                (pose, vertices) -> quad(pose, vertices, width, height));
        float ratio = Math.clamp(temperature / CrucibleBlockEntity.MAX_TEMPERATURE, 0F, 1F);
        float markerY = -height * .37F + height * .74F * ratio;
        collector.submitCustomGeometry(poses, RenderTypes.linesTranslucent(), (pose, vertices) -> {
            lineVertex(pose, vertices, -width * .32F, markerY);
            lineVertex(pose, vertices, width * .32F, markerY);
        });
        poses.popPose();
    }

    @Override public boolean shouldRenderOffScreen() { return false; }
    @Override public int getViewDistance() { return 64; }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, float width, float height) {
        float left = -width / 2F, right = width / 2F, bottom = -height / 2F, top = height / 2F;
        vertex(pose, out, left, bottom, 0, 1); vertex(pose, out, right, bottom, 1, 1);
        vertex(pose, out, right, top, 1, 0); vertex(pose, out, left, top, 0, 0);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(pose, 0, 0, 1);
    }
    private static void lineVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y) {
        out.addVertex(pose, x, y, -.004F).setColor(255, 174, 48, 255)
                .setNormal(pose, 0, 0, 1).setLineWidth(3F);
    }

    private static final class Model extends GeoModel<CrucibleBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/crucible"); }
        @Override public Identifier getTextureResource(GeoRenderState state) {
            return Asterion.id("textures/block/crucible.png");
        }
        @Override public Identifier getAnimationResource(CrucibleBlockEntity entity) { return null; }
    }
}

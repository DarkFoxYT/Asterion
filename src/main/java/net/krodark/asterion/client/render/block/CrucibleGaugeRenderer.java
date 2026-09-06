package net.krodark.asterion.client.render.block;

import net.krodark.asterion.client.light.AsterionEmissiveBuffer;
import net.krodark.asterion.client.light.AmneticBoneEmission;
import net.krodark.asterion.client.light.EmissiveBoneMesh;
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
    private static final Identifier GAUGE = Asterion.id("textures/gui/forge/temp_gauge_highres.png");
    private static final DataTicket<Float> TEMPERATURE = DataTickets.create("asterion_crucible_temperature", Float.class);
    private static final DataTicket<Boolean> GAUGE_VISIBLE = DataTickets.create("asterion_crucible_gauge_visible", Boolean.class);
    private static final DataTicket<String> MATERIALS = DataTickets.create("asterion_forge_materials", String.class);
    private static final Identifier VENT_MASK = Asterion.id("dynamic/forge_vent_emission");
    private static final DataTicket<Integer> MIX_COLOR = DataTickets.create("asterion_forge_mix_color", Integer.class);
    private static final Identifier VENT_MESH = Asterion.id("forge/vents");
    private static final Identifier FILL = Asterion.id("textures/gui/forge/left/temp_gauge_fill.png");
    private static final EmissiveBoneMesh GAUGE_MARKER = EmissiveBoneMesh.verticalPlane(.115F, .009F, -.006F);
    private static final Identifier GAUGE_MARKER_ID = Asterion.id("forge/gauge_marker");

    private static final EmissiveBoneMesh[] LIQUID = new EmissiveBoneMesh[4];
    private static final Identifier[] LIQUID_IDS = new Identifier[4];
    static {
        for (int i = 0; i < 4; i++) {
            LIQUID[i] = EmissiveBoneMesh.horizontalPlane(1.9F - i * .3F, 3.15F + i * .12F);
            LIQUID_IDS[i] = Asterion.id("forge_liquid/" + i);
        }
    }

    public CrucibleGaugeRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new Model());
        loadVentMask();
        withRenderLayer(new net.krodark.asterion.client.light.AsterionEmissiveBoneLayer<>(this, "vent_glow", VENT_MASK) {
            @Override protected boolean usesModelTextureCoordinates() { return true; }
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return !state.getOrDefaultGeckolibData(MATERIALS, "").isEmpty()
                        && state.getOrDefaultGeckolibData(TEMPERATURE, 0F) > 100;
            }
            @Override protected int emissiveColor(BlockEntityRenderState state) {
                return 0xFF000000 | state.getOrDefaultGeckolibData(MIX_COLOR, 0xFFAA44);
            }
            @Override protected float surfaceBrightness(BlockEntityRenderState state) {
                return Math.clamp((state.getOrDefaultGeckolibData(TEMPERATURE, 0F) - 100) / 500F, 0, 1);
            }
            @Override protected float emissiveStrength(BlockEntityRenderState state) {
                return surfaceBrightness(state) * .35F;
            }
            @Override protected Identifier amneticEmissionMesh(BlockEntityRenderState state) { return VENT_MESH; }
        });
    }

    private static void loadVentMask() {
        var client = Minecraft.getInstance();
        // Build one frame-sized mask during renderer loading; no per-frame pixel scans.
        try (var stream = client.getResourceManager().open(Asterion.id("textures/block/crucible.png"));
             var source = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            var mask = new com.mojang.blaze3d.platform.NativeImage(1024, 1024, true);
            // Follow the transparent vent openings on the four outer wall faces.
            int[][] faces = {{243, 162}, {243, 227}, {243, 292}, {0, 324}};
            for (int[] face : faces) for (int y = 29; y <= 48; y++) for (int x = 0; x < 80; x++) {
                int u = face[0] + x, v = face[1] + y;
                if ((source.getPixel(u, v) >>> 24) == 0) mask.setPixel(u, v, 0xFFFFFFFF);
            }
            client.getTextureManager().register(VENT_MASK,
                    new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Forge vent mask", mask));
        } catch (java.io.IOException error) {
            Asterion.LOGGER.warn("Could not load Forge vent mask", error);
        }
    }

    @Override public void addRenderData(CrucibleBlockEntity crucible, Void related,
                                        BlockEntityRenderState state, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        boolean visible = false;
        if (client.screen == null && client.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockState hitState = client.level == null ? null : client.level.getBlockState(hit.getBlockPos());
            visible = hitState != null && hitState.is(Asterion.CRUCIBLE)
                    && CrucibleBlock.root(hit.getBlockPos(), hitState).equals(crucible.getBlockPos());
        }
        state.addGeckolibData(TEMPERATURE, (float)crucible.temperature());
        state.addGeckolibData(MATERIALS, crucible.metalSequence());
        state.addGeckolibData(MIX_COLOR, crucible.mixColor());
        state.addGeckolibData(GAUGE_VISIBLE, visible);
    }

    @Override public void submit(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector collector,
                                 CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        submitContents(state, poses, collector);
        if (!state.getOrDefaultGeckolibData(GAUGE_VISIBLE, false)) return;
        float shown = state.getOrDefaultGeckolibData(TEMPERATURE, 0F);
        for (Direction side : Direction.Plane.HORIZONTAL) submitGauge(poses, collector, side, shown);
    }

    private static void submitContents(BlockEntityRenderState state, PoseStack poses, SubmitNodeCollector out) {
        String materials = state.getOrDefaultGeckolibData(MATERIALS, "");
        if (materials.isEmpty()) return;
        float heat = state.getOrDefaultGeckolibData(TEMPERATURE, 0F) / CrucibleBlockEntity.MAX_TEMPERATURE;
        for (int i = 0; i < materials.length(); i++) {
            int base = CrucibleBlockEntity.metalColor(materials.charAt(i) - '0');
            int color = net.minecraft.util.ARGB.linearLerp(heat * .4F, 0xFF000000 | base, 0xFFFFA347);
            float radius = 1.9F - i * .3F;
            float y = 3.15F + i * .12F;
            var mesh = LIQUID[i];
            var meshId = LIQUID_IDS[i];
            out.submitCustomGeometry(poses, AsterionEmissiveBuffer.renderType(FILL), (pose, vertices) -> {
                mesh.render(pose, vertices, color, 1, 1);
                if (heat > .2F) AmneticBoneEmission.submit(meshId, mesh, FILL, pose.pose(), color,
                        1, 1, heat * .35F, true);
            });
            if (i == materials.length() - 1) {
                int edge = net.minecraft.util.ARGB.linearLerp(.4F, color, 0xFF33271C);
                int shine = net.minecraft.util.ARGB.linearLerp(.22F, color, 0xFFFFE0A0);
                out.submitCustomGeometry(poses, RenderTypes.entityTranslucent(FILL, false), (pose, vertices) -> {
                    float rim = radius - .05F;
                    // Narrow strips stay inside the liquid, clear of the crucible walls.
                    strip(pose, vertices, .5F - rim, .5F + rim, y + .003F, .5F - rim, .5F - rim + .045F, edge);
                    strip(pose, vertices, .5F - rim, .5F + rim, y + .003F, .5F + rim - .045F, .5F + rim, edge);
                    strip(pose, vertices, .5F - rim * .72F, .5F + rim * .22F, y + .005F, .5F - rim * .38F, .5F - rim * .35F, shine);
                    strip(pose, vertices, .5F - rim * .12F, .5F + rim * .64F, y + .005F, .5F + rim * .34F, .5F + rim * .37F, shine);
                });
            }
        }
    }
    private static void strip(PoseStack.Pose pose, VertexConsumer out, float left, float right,
                              float y, float back, float front, int color) {
        surfaceVertex(pose, out, left, y, back, color); surfaceVertex(pose, out, left, y, front, color);
        surfaceVertex(pose, out, right, y, front, color); surfaceVertex(pose, out, right, y, back, color);
    }
    private static void surfaceVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, int color) {
        out.addVertex(pose, x, y, z).setColor(color).setUv(.5F, .5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(pose, 0, 1, 0);
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
        poses.pushPose();
        poses.translate(0, markerY, 0);
        collector.submitCustomGeometry(poses, AsterionEmissiveBuffer.renderType(FILL),
                (pose, vertices) -> GAUGE_MARKER.render(pose, vertices, 0xFFFFAE30, 1, 1));
        AmneticBoneEmission.submit(GAUGE_MARKER_ID, GAUGE_MARKER, FILL,
                poses.last().pose(), 0xFFFFAE30, 1, 1, 1.15F, false);
        poses.popPose();
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

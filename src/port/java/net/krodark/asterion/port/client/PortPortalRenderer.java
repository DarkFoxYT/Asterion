package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Visible 1.21.1 gateway renderer shared by Fabric and NeoForge. */
public final class PortPortalRenderer {
    private static final ResourceLocation PORTAL_RENDER_TYPE = Asterion.id("portal/asterion_portal");
    private static final ResourceLocation ENTRY = Asterion.id("textures/portal/asterion_portal_square.png");
    private static final ResourceLocation EXIT = Asterion.id("textures/portal/overworld_portal_square.png");
    private static ClientLevel world;
    private static GatewayPortalPayload portal;
    private static long receivedAt;

    private PortPortalRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(PortPortalRenderer::render);
    }

    public static void receive(GatewayPortalPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (!payload.active()) {
            world = null;
            portal = null;
            return;
        }
        if (world != client.level || portal == null || !portal.center().equals(payload.center())) {
            receivedAt = System.nanoTime();
        }
        world = client.level;
        portal = payload;
    }

    public static void tick(Minecraft client) {
        GatewayPortalPayload data = portal;
        if (data == null || client.level == null || client.level != world || client.player == null
                || !client.level.dimension().equals(Level.OVERWORLD) || client.level.getGameTime() % 4L != 0L) return;
        double x = data.center().getX() + .5D;
        double z = data.center().getZ() + .5D;
        double dx = client.player.getX() - x;
        double dz = client.player.getZ() - z;
        if (dx * dx + dz * dz > 1296.0D) return;

        double perimeter = (client.level.getGameTime() * .075D + (data.visualSeed() & 255L) * .013D) % 8.0D;
        int side = Mth.floor(perimeter / 2.0D);
        double along = perimeter % 2.0D - 1.0D;
        double edge = 2.22D;
        double offsetX = switch (side) {
            case 0 -> along * edge;
            case 1 -> edge;
            case 2 -> -along * edge;
            default -> -edge;
        };
        double offsetZ = switch (side) {
            case 0 -> -edge;
            case 1 -> along * edge;
            case 2 -> edge;
            default -> -along * edge;
        };
        client.level.addParticle(ParticleTypes.ASH, x + offsetX, data.surfaceY() + .08D, z + offsetZ,
                -offsetX * .003D, .014D, -offsetZ * .003D);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        GatewayPortalPayload data = portal;
        if (data == null || context.world() != world || context.matrixStack() == null || context.consumers() == null) return;
        Vec3 camera = context.camera().getPosition();
        double x = data.center().getX() + .5D;
        double y = data.surfaceY() + .035D;
        double z = data.center().getZ() + .5D;
        double distance = camera.distanceToSqr(x, y, z);
        if (distance > 19600.0D) return;

        boolean vertical = context.world().dimension().equals(Asterion.ASTERION_LEVEL);
        float opening = Mth.clamp((System.nanoTime() - receivedAt) / 1_100_000_000.0F, 0.0F, 1.0F);
        opening = opening * opening * (3.0F - 2.0F * opening);
        float pulse = 1.0F + Mth.sin((context.world().getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false)) * .075F) * .018F;
        PoseStack poses = context.matrixStack();
        poses.pushPose();
        poses.translate(x - camera.x, y - camera.y, z - camera.z);
        if (!vertical) poses.mulPose(Axis.XP.rotationDegrees(90.0F));
        poses.scale(opening * pulse * (vertical ? 1.55F : 1.48F),
                opening * pulse * (vertical ? 2.55F : 1.48F), 1.0F);

        MultiBufferSource consumers = context.consumers();
        ResourceLocation texture = vertical ? EXIT : ENTRY;
        RenderType veilType = VeilRenderType.get(PORTAL_RENDER_TYPE, texture.toString());
        RenderType renderType = veilType != null ? veilType : RenderType.entityTranslucent(texture);
        draw(consumers.getBuffer(renderType), poses.last().pose(), .003F, 0xFFFFFFFF);
        poses.popPose();
    }

    private static void draw(VertexConsumer out, Matrix4f matrix, float depth, int color) {
        vertex(out, matrix, -1, -1, depth, 0, 1, color);
        vertex(out, matrix, 1, -1, depth, 1, 1, color);
        vertex(out, matrix, 1, 1, depth, 1, 0, color);
        vertex(out, matrix, -1, 1, depth, 0, 0, color);
        vertex(out, matrix, -1, 1, -depth, 0, 0, color);
        vertex(out, matrix, 1, 1, -depth, 1, 0, color);
        vertex(out, matrix, 1, -1, -depth, 1, 1, color);
        vertex(out, matrix, -1, -1, -depth, 0, 1, color);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                               float u, float v, int color) {
        out.addVertex(matrix, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
    }
}

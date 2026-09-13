package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Depth-tested, terrain-projected Minotaur attack indicators for 1.21.1. */
public final class PortBossTelegraphs {
    private record Warning(BossTelegraphPayload shape, long received, long expires) {}
    private static final Map<Integer, Warning> WARNINGS = new LinkedHashMap<>();

    private PortBossTelegraphs() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || context.matrixStack() == null || context.consumers() == null) return;
            long now = client.level.getGameTime();
            WARNINGS.values().removeIf(warning -> now >= warning.expires);
            if (WARNINGS.isEmpty()) return;
            VertexConsumer out = context.consumers().getBuffer(RenderType.debugQuads());
            Vec3 camera = context.camera().getPosition();
            PoseStack.Pose pose = context.matrixStack().last();
            for (Warning warning : WARNINGS.values()) {
                BossTelegraphPayload shape = warning.shape;
                if (shape.center().distanceToSqr(camera) > 128 * 128) continue;
                float progress = Mth.clamp((now - warning.received)
                        / (float)Math.max(1, shape.durationTicks()), 0, 1);
                int green = Math.round(Mth.lerp(progress, 155, 42));
                int fillAlpha = Math.round(42 + progress * 48);
                int rimAlpha = Math.round(185 + progress * 65);
                drawShape(client, out, pose, camera, shape, 0xFF0000 | green << 8 | 24,
                        fillAlpha, rimAlpha);
            }
            if (context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource immediate)
                immediate.endBatch(RenderType.debugQuads());
        });
    }

    public static void receive(BossTelegraphPayload shape) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (shape.durationTicks() <= 0 || shape.radius() <= 0) {
            WARNINGS.remove(shape.ownerId());
            return;
        }
        if (WARNINGS.size() >= 16 && !WARNINGS.containsKey(shape.ownerId()))
            WARNINGS.remove(WARNINGS.keySet().iterator().next());
        long now = client.level.getGameTime();
        WARNINGS.put(shape.ownerId(), new Warning(shape, now, now + Math.min(200, shape.durationTicks())));
    }

    private static void drawShape(Minecraft client, VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                  BossTelegraphPayload shape, int rgb, int fillAlpha, int rimAlpha) {
        Vec3 forward = safe(new Vec3(shape.direction().x, 0, shape.direction().z), new Vec3(0, 0, 1));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (shape.kind() == BossTelegraphPayload.CHARGE_LANE) {
            double width = Mth.clamp(shape.halfWidth(), .25F, 12.0F);
            int length = Math.max(1, Mth.ceil(shape.radius()));
            for (int i = 0; i < length; i++) {
                Vec3 a = forward.scale(shape.radius() * i / length);
                Vec3 b = forward.scale(shape.radius() * (i + 1) / length);
                quad(client, out, pose, camera, shape.center(), a.add(right.scale(-width)),
                        b.add(right.scale(-width)), b.add(right.scale(width)), a.add(right.scale(width)),
                        rgb, fillAlpha);
            }
            line(client, out, pose, camera, shape.center(), right.scale(-width),
                    forward.scale(shape.radius()).add(right.scale(-width)), rgb, rimAlpha);
            line(client, out, pose, camera, shape.center(), right.scale(width),
                    forward.scale(shape.radius()).add(right.scale(width)), rgb, rimAlpha);
            line(client, out, pose, camera, shape.center(), forward.scale(shape.radius()).add(right.scale(-width)),
                    forward.scale(shape.radius()).add(right.scale(width)), rgb, rimAlpha);
            return;
        }
        boolean box = shape.kind() == BossTelegraphPayload.BOX || shape.kind() == BossTelegraphPayload.BOX_CONE;
        double arc = Mth.clamp(shape.arcRadians(), .01F, Mth.TWO_PI);
        double start = Math.atan2(forward.z, forward.x) - arc * .5D;
        int segments = Math.max(20, Mth.ceil(arc * 12));
        for (int i = 0; i < segments; i++) {
            Vec3 a = radial(start + arc * i / segments, shape.radius(), box);
            Vec3 b = radial(start + arc * (i + 1) / segments, shape.radius(), box);
            quad(client, out, pose, camera, shape.center(), Vec3.ZERO, a, b, Vec3.ZERO, rgb, fillAlpha);
            line(client, out, pose, camera, shape.center(), a, b, rgb, rimAlpha);
        }
        if (arc < Mth.TWO_PI - .01D) {
            line(client, out, pose, camera, shape.center(), Vec3.ZERO, radial(start, shape.radius(), box), rgb, rimAlpha);
            line(client, out, pose, camera, shape.center(), Vec3.ZERO, radial(start + arc, shape.radius(), box), rgb, rimAlpha);
        }
    }

    private static Vec3 radial(double angle, double radius, boolean box) {
        double x = Math.cos(angle), z = Math.sin(angle);
        if (box) radius /= Math.max(Math.abs(x), Math.abs(z));
        return new Vec3(x * radius, 0, z * radius);
    }

    private static void line(Minecraft client, VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 center,
                             Vec3 a, Vec3 b, int rgb, int alpha) {
        Vec3 side = safe(b.subtract(a), new Vec3(0, 0, 1));
        side = new Vec3(-side.z, 0, side.x).scale(.075D);
        quad(client, out, pose, camera, center, a.add(side), b.add(side), b.subtract(side), a.subtract(side), rgb, alpha);
    }

    private static void quad(Minecraft client, VertexConsumer out, PoseStack.Pose pose, Vec3 camera, Vec3 center,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
        Vec3 pa = project(client, center.add(a)), pb = project(client, center.add(b));
        Vec3 pc = project(client, center.add(c)), pd = project(client, center.add(d));
        if (pa == null || pb == null || pc == null || pd == null) return;
        double min = Math.min(Math.min(pa.y, pb.y), Math.min(pc.y, pd.y));
        double max = Math.max(Math.max(pa.y, pb.y), Math.max(pc.y, pd.y));
        if (max - min > .75D) return;
        vertex(out, pose, pa.subtract(camera), rgb, alpha);
        vertex(out, pose, pb.subtract(camera), rgb, alpha);
        vertex(out, pose, pc.subtract(camera), rgb, alpha);
        vertex(out, pose, pd.subtract(camera), rgb, alpha);
    }

    private static Vec3 project(Minecraft client, Vec3 point) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(point.x), Mth.floor(point.y) + 2,
                Mth.floor(point.z));
        for (int y = pos.getY(); y >= Math.max(client.level.getMinBuildHeight(), point.y - 32); y--) {
            pos.setY(y);
            var collision = client.level.getBlockState(pos).getCollisionShape(client.level, pos);
            if (!collision.isEmpty()) return new Vec3(point.x, y + collision.max(Direction.Axis.Y) + .035D, point.z);
        }
        return null;
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int rgb, int alpha) {
        out.addVertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
    }

    private static Vec3 safe(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }
}

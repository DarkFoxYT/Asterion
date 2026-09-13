package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.MazeZapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Packet-driven branched lightning renderer shared by Fabric and NeoForge. */
public final class PortLightning {
    private static final List<Zap> ZAPS = new ArrayList<>();
    private static final List<GroundZap> GROUND = new ArrayList<>();

    private PortLightning() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || context.matrixStack() == null || context.consumers() == null) return;
            Vec3 camera = context.camera().getPosition();
            long now = client.level.getGameTime();
            VertexConsumer out = context.consumers().getBuffer(RenderType.lightning());
            for (Zap zap : ZAPS) {
                if (now > zap.expires) continue;
                Entity target = client.level.getEntity(zap.entityId);
                if (target == null) continue;
                Vec3 body = target.position().add(0, target.getBbHeight() * 0.52D, 0);
                draw(out, context.matrixStack().last(), zap.source.subtract(camera), body.subtract(camera),
                        Math.min(1.0F, (zap.expires - now + 1) / 4.0F), zap.seed + now / 2L);
                for (int arc = 0; arc < 3; arc++) {
                    double phase = now * 0.57D + zap.entityId * 0.91D + arc * 2.094D;
                    Vec3 a = body.add(Math.cos(phase) * target.getBbWidth() * 0.6D,
                            Math.sin(phase * 1.37D) * target.getBbHeight() * 0.35D,
                            Math.sin(phase) * target.getBbWidth() * 0.6D);
                    Vec3 b = body.add(Math.cos(phase + 1.1D) * target.getBbWidth() * 0.52D,
                            Math.sin((phase + 1.1D) * 1.21D) * target.getBbHeight() * 0.38D,
                            Math.sin(phase + 1.1D) * target.getBbWidth() * 0.52D);
                    draw(out, context.matrixStack().last(), a.subtract(camera), b.subtract(camera),
                            0.62F, zap.seed + arc * 101L + now / 3L);
                }
            }
            for (GroundZap zap : GROUND) {
                if (now < zap.start || now > zap.expires) continue;
                Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                        AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
                Vec3 target = Vec3.atCenterOf(zap.target);
                draw(out, context.matrixStack().last(), source.subtract(camera), target.subtract(camera),
                        1.0F - (now - zap.start) / (float)Math.max(1, zap.expires - zap.start), zap.seed + now / 2L);
            }
        });
    }

    public static void receive(MazeZapPayload payload) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0 : client.level.getGameTime();
        ZAPS.removeIf(zap -> zap.entityId == payload.targetEntityId());
        ZAPS.add(new Zap(payload.targetEntityId(), payload.source(), now + payload.durationTicks(),
                Double.doubleToLongBits(payload.source().x + payload.source().z)));
    }

    public static void receive(DeadSunStrikePayload payload) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0 : client.level.getGameTime();
        long start = now + Math.max(0, payload.warningTicks());
        GROUND.add(new GroundZap(payload.target(), start, start + 12, payload.seed()));
    }

    public static void tick(Minecraft client) {
        if (client.level == null) { ZAPS.clear(); GROUND.clear(); return; }
        long now = client.level.getGameTime();
        ZAPS.removeIf(zap -> now > zap.expires || client.level.getEntity(zap.entityId) == null);
        GROUND.removeIf(zap -> now > zap.expires);
    }

    private static void draw(VertexConsumer out, PoseStack.Pose pose, Vec3 start, Vec3 end,
                             float strength, long seed) {
        double distance = start.distanceTo(end);
        if (distance < 0.02D) return;
        Random random = new Random(seed);
        int depth = distance > 70 ? 5 : 4;
        Vec3[] path = path(start, end, depth, random);
        float width = (float)Mth.clamp(0.055D + Math.sqrt(distance) * 0.007D, 0.06D, 0.18D)
                * (0.65F + strength * 0.35F);
        ribbonPath(out, pose, path, width * 1.8F, 255, 38, 16, 92);
        ribbonPath(out, pose, path, width, 255, 112, 62, 235);
        ribbonPath(out, pose, path, width * 0.24F, 255, 232, 205, 255);
        int branches = Mth.clamp(3 + (int)(distance / 20), 3, 12);
        for (int i = 0; i < branches && path.length > 5; i++) {
            int anchor = 2 + random.nextInt(path.length - 4);
            Vec3 a = path[anchor];
            Vec3 tangent = path[anchor + 1].subtract(path[anchor - 1]).normalize();
            Vec3 side = perpendicular(tangent);
            Vec3 tip = a.add(tangent.scale(0.8D + random.nextDouble() * 2.0D))
                    .add(side.scale((random.nextDouble() * 2 - 1) * Math.min(7, distance * 0.12D)));
            Vec3[] branch = path(a, tip, 3, random);
            ribbonPath(out, pose, branch, width * 0.55F, 255, 68, 34, 176);
            ribbonPath(out, pose, branch, width * 0.14F, 255, 220, 190, 245);
        }
    }

    private static Vec3[] path(Vec3 start, Vec3 end, int depth, Random random) {
        List<Vec3> points = new ArrayList<>();
        points.add(start); points.add(end);
        double length = start.distanceTo(end);
        for (int pass = 0; pass < depth; pass++) {
            List<Vec3> next = new ArrayList<>();
            double amplitude = Math.min(3.2D, Math.sqrt(length) * 0.55D * Math.pow(0.52D, pass));
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i), b = points.get(i + 1);
                Vec3 tangent = b.subtract(a).normalize();
                Vec3 jitter = perpendicular(tangent).scale((random.nextDouble() * 2 - 1) * amplitude);
                next.add(a); next.add(a.add(b).scale(0.5D).add(jitter));
            }
            next.add(end); points = next;
        }
        return points.toArray(Vec3[]::new);
    }

    private static void ribbonPath(VertexConsumer out, PoseStack.Pose pose, Vec3[] path, float width,
                                   int red, int green, int blue, int alpha) {
        for (int i = 0; i < path.length - 1; i++) {
            Vec3 a = path[i], b = path[i + 1];
            Vec3 side = perpendicular(b.subtract(a).normalize());
            float wa = width * (0.34F + Mth.sin(i / (float)(path.length - 1) * Mth.PI) * 0.66F);
            float wb = width * (0.34F + Mth.sin((i + 1) / (float)(path.length - 1) * Mth.PI) * 0.66F);
            Vec3 ao = a.add(side.scale(wa)), ai = a.subtract(side.scale(wa));
            Vec3 bo = b.add(side.scale(wb)), bi = b.subtract(side.scale(wb));
            vertex(out, pose, ao, red, green, blue, alpha); vertex(out, pose, ai, red, green, blue, alpha);
            vertex(out, pose, bi, red, green, blue, alpha); vertex(out, pose, bo, red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p,
                               int red, int green, int blue, int alpha) {
        out.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(red, green, blue, alpha);
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 seed = Math.abs(direction.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 result = direction.cross(seed);
        return result.lengthSqr() < 1.0E-8D ? new Vec3(1, 0, 0) : result.normalize();
    }

    private record Zap(int entityId, Vec3 source, long expires, long seed) {}
    private record GroundZap(net.minecraft.core.BlockPos target, long start, long expires, long seed) {}
}

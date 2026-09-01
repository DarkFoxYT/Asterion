package net.krodark.asterion.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ground-projected, depth-tested warning surfaces. Cached geometry; no particles, textures or bloom. */
public final class BossGroundTelegraphRenderer {
    private static final RenderType SURFACE = AmneticRenderTypeAccess.create("asterion/boss_ground_warning",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Asterion.id("pipeline/boss_ground_warning")).withCull(false).build())
                    .createRenderSetup());
    private static final Map<Integer, Warning> WARNINGS = new LinkedHashMap<>();
    private static ClientLevel trackedLevel;
    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean rim) { }
    private record Warning(BossTelegraphPayload shape, List<Quad> mesh, long received, long expires) { }
    private BossGroundTelegraphRenderer() { }

    public static void clear() { WARNINGS.clear(); trackedLevel = null; }
    public static int activeCount() { return WARNINGS.size(); }
    public static void receive(BossTelegraphPayload shape) {
        ClientLevel level = Minecraft.getInstance().level;
        if (trackedLevel != level) { clear(); trackedLevel = level; }
        if (level == null) return;
        if (shape.durationTicks() <= 0 || shape.radius() <= 0) { WARNINGS.remove(shape.ownerId()); return; }
        if (!Double.isFinite(shape.center().lengthSqr()) || !Double.isFinite(shape.direction().lengthSqr())
                || !Float.isFinite(shape.radius()) || shape.radius() > 96 || !Float.isFinite(shape.arcRadians())
                || !Float.isFinite(shape.halfWidth()) || !Float.isFinite(shape.progress())
                || shape.kind() < 0 || shape.kind() > BossTelegraphPayload.BOX_CONE) return;
        Warning old = WARNINGS.get(shape.ownerId());
        List<Quad> mesh = old != null && sameGeometry(old.shape, shape) ? old.mesh : buildMesh(level, shape);
        if (WARNINGS.size() >= 16 && old == null) WARNINGS.remove(WARNINGS.keySet().iterator().next());
        long now = level.getGameTime();
        WARNINGS.put(shape.ownerId(), new Warning(shape, mesh, now, now + Math.min(200, shape.durationTicks())));
    }
    private static boolean sameGeometry(BossTelegraphPayload a, BossTelegraphPayload b) {
        return a.center().equals(b.center()) && a.direction().equals(b.direction()) && a.radius() == b.radius()
                && a.kind() == b.kind() && a.arcRadians() == b.arcRadians() && a.halfWidth() == b.halfWidth();
    }
    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            var client = Minecraft.getInstance();
            if (client.level != trackedLevel) { clear(); trackedLevel = client.level; }
            if (client.level == null) return;
            long now = client.level.getGameTime();
            WARNINGS.values().removeIf(w -> now >= w.expires);
            if (WARNINGS.isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            var out = context.bufferSource().getBuffer(SURFACE);
            var pose = context.poseStack().last();
            for (Warning warning : WARNINGS.values()) {
                var owner = client.level.getEntity(warning.shape.ownerId());
                if ((owner != null && !owner.isAlive())
                        || warning.shape.center().distanceToSqr(camera) > 128 * 128) continue;
                float progress = warning.shape.durationTicks() <= 3 ? warning.shape.progress()
                        : (now - warning.received) / (float)warning.shape.durationTicks();
                progress = Mth.clamp(progress, 0, 1);
                int green = Math.round(Mth.lerp(progress, 155, 48));
                int fillAlpha = Math.round(35 + progress * 40);
                int rimAlpha = Math.round(175 + progress * 65);
                for (Quad quad : warning.mesh) {
                    int color = ((quad.rim ? rimAlpha : fillAlpha) << 24) | 0xFF0000 | (green << 8) | 24;
                    vertex(out, pose, quad.a, camera, color); vertex(out, pose, quad.b, camera, color);
                    vertex(out, pose, quad.c, camera, color); vertex(out, pose, quad.d, camera, color);
                }
            }
            context.bufferSource().endBatch(SURFACE);
        });
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point, Vec3 camera, int color) {
        out.addVertex(pose, (float)(point.x-camera.x), (float)(point.y-camera.y), (float)(point.z-camera.z)).setColor(color);
    }

    private static List<Quad> buildMesh(ClientLevel level, BossTelegraphPayload shape) {
        var builder = new GroundMesh(level, shape.center());
        Vec3 forward = new Vec3(shape.direction().x, 0, shape.direction().z).normalize();
        if (forward.lengthSqr() < .01) forward = new Vec3(0, 0, 1);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (shape.kind() == BossTelegraphPayload.CHARGE_LANE) {
            double width = Math.clamp(shape.halfWidth(), .15, 12);
            int lengthSteps = Mth.ceil(shape.radius()), widthSteps = Math.max(1, Mth.ceil(width * 2));
            for (int i = 0; i < lengthSteps; i++) for (int j = 0; j < widthSteps; j++) {
                double a = shape.radius() * i / lengthSteps, b = shape.radius() * (i + 1) / lengthSteps;
                double l = -width + 2 * width * j / widthSteps, r = -width + 2 * width * (j + 1) / widthSteps;
                builder.quad(forward.scale(a).add(right.scale(l)), forward.scale(b).add(right.scale(l)),
                        forward.scale(b).add(right.scale(r)), forward.scale(a).add(right.scale(r)), false);
            }
            builder.line(right.scale(-width), forward.scale(shape.radius()).add(right.scale(-width)));
            builder.line(right.scale(width), forward.scale(shape.radius()).add(right.scale(width)));
            builder.line(forward.scale(shape.radius()).add(right.scale(-width)), forward.scale(shape.radius()).add(right.scale(width)));
            // Chevrons make the committed travel direction readable at a glance.
            for (double d = 3; d < shape.radius(); d += 4) {
                builder.line(forward.scale(d - 1).add(right.scale(-width * .6)), forward.scale(d));
                builder.line(forward.scale(d - 1).add(right.scale(width * .6)), forward.scale(d));
            }
        } else {
            boolean box = shape.kind() == BossTelegraphPayload.BOX || shape.kind() == BossTelegraphPayload.BOX_CONE;
            double arc = Math.clamp(shape.arcRadians(), .01, Math.PI * 2);
            double start = Math.atan2(forward.z, forward.x) - arc * .5;
            int segments = Math.max(18, Mth.ceil(arc * 12));
            int rings = Math.max(1, Mth.ceil(shape.radius()));
            for (int i = 0; i < segments; i++) {
                Vec3 a = radial(start + arc * i / segments, shape.radius(), box);
                Vec3 b = radial(start + arc * (i + 1) / segments, shape.radius(), box);
                for (int ring = 0; ring < rings; ring++) {
                    double inner = ring / (double)rings, outer = (ring + 1) / (double)rings;
                    builder.quad(a.scale(inner), a.scale(outer), b.scale(outer), b.scale(inner), false);
                }
                builder.line(a, b);
            }
            if (arc < Math.PI * 2 - .01) {
                builder.line(Vec3.ZERO, radial(start, shape.radius(), box));
                builder.line(Vec3.ZERO, radial(start + arc, shape.radius(), box));
            }
        }
        return List.copyOf(builder.quads);
    }
    private static Vec3 radial(double angle, double radius, boolean box) {
        double x = Math.cos(angle), z = Math.sin(angle);
        if (box) radius /= Math.max(Math.abs(x), Math.abs(z));
        return new Vec3(x * radius, 0, z * radius);
    }
    private static final class GroundMesh {
        final ClientLevel level; final Vec3 center;
        final List<Quad> quads = new ArrayList<>();
        final Map<Long, Double> heights = new HashMap<>();
        GroundMesh(ClientLevel level, Vec3 center) { this.level = level; this.center = center; }
        Vec3 project(Vec3 local) {
            double x = center.x + local.x, z = center.z + local.z;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            long key = ((long)bx << 32) ^ (bz & 0xffffffffL);
            double y = heights.computeIfAbsent(key, ignored -> {
                var pos = new BlockPos.MutableBlockPos(bx, Mth.floor(center.y) + 1, bz);
                if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
                for (int h = pos.getY(); h >= Math.max(level.getMinY(), center.y - 32); h--) {
                    pos.setY(h);
                    var collision = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!collision.isEmpty()) return h + collision.max(Direction.Axis.Y) + .035;
                }
                return Double.NaN;
            });
            return new Vec3(x, y, z);
        }
        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean rim) {
            a = project(a); b = project(b); c = project(c); d = project(d);
            double min = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
            double max = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
            // Never bridge pits or stretch a warning up a wall; the surface stays on walkable ground.
            if (!Double.isFinite(min) || !Double.isFinite(max) || max - min > .65) return;
            quads.add(new Quad(a, b, c, d, rim));
        }
        void line(Vec3 a, Vec3 b) {
            Vec3 delta = b.subtract(a), side = new Vec3(-delta.z, 0, delta.x).normalize().scale(.065);
            int steps = Math.max(1, Mth.ceil(delta.length()));
            for (int i = 0; i < steps; i++) {
                Vec3 p = a.lerp(b, i / (double)steps), q = a.lerp(b, (i + 1) / (double)steps);
                quad(p.add(side), q.add(side), q.subtract(side), p.subtract(side), true);
            }
        }
    }
}

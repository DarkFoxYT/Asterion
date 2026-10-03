package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class PortBossTelegraphs {
    private static final RenderType SURFACE=RenderType.debugQuads();
    private static final Map<Integer, Warning> WARNINGS = new LinkedHashMap<>();
    private static final Map<Integer, Warning> SCARS = new LinkedHashMap<>();
    private static ClientLevel trackedLevel;
    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean rim) { }
    private record Warning(BossTelegraphPayload shape, List<Quad> mesh, long received, long expires, long meshTick) { }
    private PortBossTelegraphs() { }

    public static void clear() { WARNINGS.clear(); SCARS.clear(); trackedLevel = null; }
    public static int activeCount() { return WARNINGS.size(); }
    public static void receive(BossTelegraphPayload shape) {
        ClientLevel level = Minecraft.getInstance().level;
        if (trackedLevel != level) { clear(); trackedLevel = level; }
        if (level == null) return;
        if (shape.durationTicks() <= 0 || shape.radius() <= 0) { WARNINGS.remove(shape.ownerId()); return; }
        if (!Double.isFinite(shape.center().lengthSqr()) || !Double.isFinite(shape.direction().lengthSqr())
                || !Float.isFinite(shape.radius()) || shape.radius() > 96 || !Float.isFinite(shape.arcRadians())
                || !Float.isFinite(shape.halfWidth()) || !Float.isFinite(shape.progress())
                || shape.kind() < 0 || shape.kind() > BossTelegraphPayload.WALL_SCAR) return;
        if (shape.kind() == BossTelegraphPayload.SCAR || shape.kind() == BossTelegraphPayload.WALL_SCAR) {
            if (SCARS.size() >= 128) SCARS.remove(SCARS.keySet().iterator().next());
            long now = level.getGameTime();
            SCARS.put(shape.ownerId(), new Warning(shape, buildMesh(level, shape), now, now + Math.min(600, shape.durationTicks()), now));
            return;
        }
        Warning old = WARNINGS.get(shape.ownerId());
        boolean reuse = old != null && level.getGameTime() - old.meshTick < 10 && sameGeometry(old.shape, shape);
        List<Quad> mesh = reuse ? old.mesh : buildMesh(level, shape);
        if (WARNINGS.size() >= 16 && old == null) WARNINGS.remove(WARNINGS.keySet().iterator().next());
        long now = level.getGameTime();
        WARNINGS.put(shape.ownerId(), new Warning(shape, mesh, now, now + Math.min(200, shape.durationTicks()), reuse ? old.meshTick : now));
    }
    private static boolean sameGeometry(BossTelegraphPayload a, BossTelegraphPayload b) {
        return a.center().equals(b.center()) && a.direction().equals(b.direction()) && a.radius() == b.radius()
                && a.kind() == b.kind() && a.arcRadians() == b.arcRadians() && a.halfWidth() == b.halfWidth();
    }
    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var client = Minecraft.getInstance();
            if (client.level != trackedLevel) { clear(); trackedLevel = client.level; }
            if (client.level == null || context.matrixStack()==null || context.consumers()==null) return;
            long now = client.level.getGameTime();
            WARNINGS.values().removeIf(w -> now >= w.expires);
            SCARS.values().removeIf(w -> now >= w.expires);
            if (WARNINGS.isEmpty() && SCARS.isEmpty()) return;
            Vec3 camera = context.camera().getPosition();
            var out = context.consumers().getBuffer(SURFACE);
            var pose = context.matrixStack().last();
            for (Warning scar : SCARS.values()) {
                if (scar.shape.center().distanceToSqr(camera) > 96 * 96) continue;
                int alpha = Math.round(185 * net.krodark.asterion.port.compat.MathCompat.clamp((scar.expires - now) / 60F, 0, 1));
                for (Quad quad : scar.mesh) {
                    int color = alpha << 24 | 0x0C0B09;
                    vertex(out, pose, quad.a, camera, color); vertex(out, pose, quad.b, camera, color);
                    vertex(out, pose, quad.c, camera, color); vertex(out, pose, quad.d, camera, color);
                }
            }
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
            if(context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource immediate)immediate.endBatch(SURFACE);
        });
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point, Vec3 camera, int color) {
        //? if >=1.20.5 {
        out.addVertex(pose, (float)(point.x-camera.x), (float)(point.y-camera.y), (float)(point.z-camera.z)).setColor(color);
        //?} else {
        /*        out.vertex(pose.pose(), (float)(point.x-camera.x), (float)(point.y-camera.y), (float)(point.z-camera.z)).color(color).endVertex();*/
        //?}

    }

    private static List<Quad> buildMesh(ClientLevel level, BossTelegraphPayload shape) {
        if (shape.kind() == BossTelegraphPayload.WALL_SCAR) {
            Vec3 normal = shape.direction().normalize();
            if (Math.abs(normal.y) > .01 || normal.lengthSqr() < .99) return List.of();
            Vec3 up = new Vec3(0,1,0), right = normal.cross(up);
            Vec3 tangent = up.scale(Math.sin(shape.arcRadians())).add(right.scale(Math.cos(shape.arcRadians())));
            Vec3 side = normal.cross(tangent).scale(net.krodark.asterion.port.compat.MathCompat.clamp(shape.halfWidth(), .03, .15));
            List<Quad> mesh = new ArrayList<>();
            for (int i=0;i<8;i++) {
                Vec3 a=shape.center().add(tangent.scale(shape.radius()*(i/8.0-.5)));
                Vec3 b=shape.center().add(tangent.scale(shape.radius()*((i+1)/8.0-.5)));
                Vec3[] corners={a.add(side),b.add(side),b.subtract(side),a.subtract(side)};
                boolean supported=true;
                for(Vec3 point:corners) {
                    BlockPos pos=BlockPos.containing(point.subtract(normal.scale(.08)));
                    if(!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)
                            || !level.getFluidState(pos).isEmpty() || !level.getBlockState(pos).isCollisionShapeFullBlock(level,pos)) {supported=false;break;}
                }
                if(supported)mesh.add(new Quad(corners[0],corners[1],corners[2],corners[3],false));
            }
            return List.copyOf(mesh);
        }
        var builder = new GroundMesh(level, shape.center());
        Vec3 forward = new Vec3(shape.direction().x, 0, shape.direction().z).normalize();
        if (forward.lengthSqr() < .01) forward = new Vec3(0, 0, 1);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (shape.kind() == BossTelegraphPayload.SCAR) {
            Vec3 side = right.scale(net.krodark.asterion.port.compat.MathCompat.clamp(shape.halfWidth(), .03, .3));
            int segments = Math.max(1, Mth.ceil(shape.radius() * 2));
            for (int i = 0; i < segments; i++) {
                Vec3 a = forward.scale(shape.radius() * i / segments), b = forward.scale(shape.radius() * (i + 1) / segments);
                builder.quad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), false);
            }
        } else if (shape.kind() == BossTelegraphPayload.CHARGE_LANE) {
            double width = net.krodark.asterion.port.compat.MathCompat.clamp(shape.halfWidth(), .15, 12);
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


        } else {
            boolean box = shape.kind() == BossTelegraphPayload.BOX || shape.kind() == BossTelegraphPayload.BOX_CONE;
            double arc = net.krodark.asterion.port.compat.MathCompat.clamp(shape.arcRadians(), .01, Math.PI * 2);
            double start = Math.atan2(forward.z, forward.x) - arc * .5;
            int segments = Math.max(18, Mth.ceil(arc * 12));
            double innerRadius=shape.kind()==BossTelegraphPayload.RING?Math.max(0,shape.radius()-shape.halfWidth()*2):0;
            int rings = Math.max(1, Mth.ceil(shape.radius()-innerRadius));
            for (int i = 0; i < segments; i++) {
                Vec3 a = radial(start + arc * i / segments, shape.radius(), box);
                Vec3 b = radial(start + arc * (i + 1) / segments, shape.radius(), box);
                for (int ring = 0; ring < rings; ring++) {
                    double minimum=innerRadius/shape.radius();
                    double inner = minimum+(1-minimum)*ring/rings, outer = minimum+(1-minimum)*(ring+1)/rings;
                    builder.quad(a.scale(inner), a.scale(outer), b.scale(outer), b.scale(inner), false);
                }
                builder.line(a, b);
                if(innerRadius>0)builder.line(a.scale(innerRadius/shape.radius()),b.scale(innerRadius/shape.radius()));
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
            long key = ((long)Mth.floor(x * 8) << 32) ^ (Mth.floor(z * 8) & 0xffffffffL);
            double y = heights.computeIfAbsent(key, ignored -> {
                var pos = new BlockPos.MutableBlockPos(bx, Mth.floor(center.y) + 3, bz);
                if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
                for (int h = pos.getY(); h >= Math.max(level.getMinBuildHeight(), center.y - 32); h--) {
                    pos.setY(h);
                    if (!level.getFluidState(pos).isEmpty()) return Double.NaN;
                    var collision = level.getBlockState(pos).getCollisionShape(level, pos);
                    double top = Double.NEGATIVE_INFINITY;
                    for (var box : collision.toAabbs()) {
                        if (x - bx >= box.minX - .0001 && x - bx <= box.maxX + .0001
                                && z - bz >= box.minZ - .0001 && z - bz <= box.maxZ + .0001)
                            top = Math.max(top, box.maxY);
                    }
                    if (Double.isFinite(top)) return h + top + .085;
                }
                return Double.NaN;
            });
            return new Vec3(x, y, z);
        }
        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean rim) {
            int u = Math.max(1, Mth.ceil(Math.max(a.distanceTo(b), d.distanceTo(c)) * 2));
            int v = Math.max(1, Mth.ceil(Math.max(a.distanceTo(d), b.distanceTo(c)) * 2));
            for (int i=0;i<u;i++) for(int j=0;j<v;j++) {
                Vec3 left=a.lerp(b,i/(double)u), right=a.lerp(b,(i+1)/(double)u);
                Vec3 leftEnd=d.lerp(c,i/(double)u), rightEnd=d.lerp(c,(i+1)/(double)u);
                tile(left.lerp(leftEnd,j/(double)v),right.lerp(rightEnd,j/(double)v),
                        right.lerp(rightEnd,(j+1)/(double)v),left.lerp(leftEnd,(j+1)/(double)v),rim);
            }
        }
        void tile(Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean rim) {
            Vec3 middle=project(a.add(b).add(c).add(d).scale(.25));
            a = project(a); b = project(b); c = project(c); d = project(d);
            double min = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
            double max = Math.max(middle.y, Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));

            if (!Double.isFinite(min) || !Double.isFinite(max) || max - min > .65) return;
            quads.add(new Quad(new Vec3(a.x,max,a.z),new Vec3(b.x,max,b.z),new Vec3(c.x,max,c.z),new Vec3(d.x,max,d.z),rim));
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

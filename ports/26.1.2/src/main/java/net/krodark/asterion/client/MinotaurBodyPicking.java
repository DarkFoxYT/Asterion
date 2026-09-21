package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.MinotaurBodyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class MinotaurBodyPicking {
    private static final Map<MinotaurEntity, Body> BODIES = new WeakHashMap<>();
    private MinotaurBodyPicking() {}

    public static final class Body {
        private final long tick;
        private final java.lang.ref.WeakReference<MinotaurEntity> owner;
        private final List<Part> parts = new ArrayList<>();
        private Body(MinotaurEntity boss) {
            this.tick = boss.level().getGameTime();
            this.owner = new java.lang.ref.WeakReference<>(boss);
        }
        public void add(Matrix4f inverse, Vec3 camera, List<AABB> shapes, int region) {
            parts.add(new Part(inverse, camera, shapes, region));
        }
        public int partCount() { return parts.size(); }
        public Vec3 clip(Vec3 from, Vec3 to) {
            var hit = pick(from, to);
            return hit == null ? null : hit.point();
        }
        public BodyHit pick(Vec3 from, Vec3 to) {
            BodyHit best = null;
            double distance = Double.POSITIVE_INFINITY;
            for (var part : parts) {
                Vec3 localFrom = transform(part.inverse, from.subtract(part.camera));
                Vec3 localTo = transform(part.inverse, to.subtract(part.camera));
                double length = localFrom.distanceTo(localTo);
                if (length < 1e-8) continue;
                for (var shape : part.shapes) {
                    var hit = shape.contains(localFrom) ? java.util.Optional.of(localFrom) : shape.clip(localFrom, localTo);
                    if (hit.isEmpty()) continue;
                    double fraction = localFrom.distanceTo(hit.get()) / length;
                    if (fraction < distance) { distance = fraction; best = new BodyHit(from.lerp(to, fraction), part.region); }
                }
            }
            return best;
        }
    }
    public record BodyHit(Vec3 point, int part) {}
    private record Part(Matrix4f inverse, Vec3 camera, List<AABB> shapes, int region) {}

    public static Body begin(MinotaurEntity boss) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.player.distanceToSqr(boss) > 32 * 32) return null;
        return new Body(boss);
    }

    public static void publish(Body body) {
        if (body == null || body.parts.isEmpty()) return;
        var boss = body.owner.get();
        if (boss != null) BODIES.put(boss, body);
    }

    public static Body body(MinotaurEntity boss) { return BODIES.get(boss); }

    public static void pick(Minecraft client, float partial) {
        if (client.player == null || client.level == null) { BODIES.clear(); return; }
        if (client.getCameraEntity() != client.player) return;
        BODIES.entrySet().removeIf(entry -> !entry.getKey().isAlive()
                || entry.getKey().level() != client.level || client.level.getGameTime() - entry.getValue().tick > 2);
        if (BODIES.isEmpty()) return;
        Vec3 eye = client.player.getEyePosition(partial);
        Vec3 end = eye.add(client.player.getViewVector(partial).scale(client.player.entityInteractionRange()));
        var block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        double limit = eye.distanceToSqr(block.getLocation());
        if (client.hitResult instanceof EntityHitResult existing && !(existing.getEntity() instanceof MinotaurEntity))
            limit = Math.min(limit, eye.distanceToSqr(existing.getLocation()));
        EntityHitResult nearest = null;
        for (var entry : BODIES.entrySet()) {
            var boss = entry.getKey();
            var body = entry.getValue();
            if (!boss.isAlive() || boss.level() != client.level || client.level.getGameTime() - body.tick > 2) continue;
            Vec3 hit = body.clip(eye, end);
            if (hit != null && eye.distanceToSqr(hit) <= limit) {
                limit = eye.distanceToSqr(hit);
                nearest = new EntityHitResult(boss, hit);
            }
        }
        if (nearest != null) {
            client.hitResult = nearest;
            client.crosshairPickEntity = nearest.getEntity();
        } else if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof MinotaurEntity boss
                && BODIES.containsKey(boss) && client.level.getGameTime() - BODIES.get(boss).tick <= 2) {
            client.hitResult = block;
            client.crosshairPickEntity = null;
        }
    }

    public static boolean interact(Minecraft client, boolean attack) {
        if (client.player == null || client.player.isSpectator() || client.player.isUsingItem()
                || !(client.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof MinotaurEntity boss)
                || (!attack && !boss.isDefeatedBoss()) || !ClientPlayNetworking.canSend(MinotaurBodyPayload.TYPE)) return false;
        if (attack && client.player.cannotAttackWithItem(client.player.getMainHandItem(), 0)) return true;
        var body = BODIES.get(boss);
        Vec3 eye = client.player.getEyePosition();
        Vec3 direction = hit.getLocation().subtract(eye).normalize();
        var part = body == null ? null : body.pick(eye, hit.getLocation().add(direction.scale(.1)));
        ClientPlayNetworking.send(new MinotaurBodyPayload(boss.getId(), hit.getLocation(), attack, part == null ? -1 : part.part()));
        client.player.swing(InteractionHand.MAIN_HAND);
        if (attack) client.player.resetAttackStrengthTicker();
        return true;
    }

    private static Vec3 transform(Matrix4f matrix, Vec3 point) {
        Vector3f v = matrix.transformPosition(new Vector3f((float)point.x, (float)point.y, (float)point.z));
        return new Vec3(v.x, v.y, v.z);
    }
}

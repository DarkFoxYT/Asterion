package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.MinotaurRemains;
import net.krodark.asterion.network.MinotaurBodyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Enlarged model-aware selection and region picking for the authored Minotaur skeleton. */
public final class PortMinotaurBodyPicking {
    private PortMinotaurBodyPicking() {}

    public static void pick(Minecraft client, float partial) {
        if (client.player == null || client.level == null || client.getCameraEntity() != client.player) return;
        Vec3 eye = client.player.getEyePosition(partial);
        Vec3 end = eye.add(client.player.getViewVector(partial).scale(net.krodark.asterion.port.compat.EntityCompat.reach(client.player)));
        HitResult block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, client.player));
        double closest = block.getType() == HitResult.Type.MISS ? eye.distanceToSqr(end)
                : eye.distanceToSqr(block.getLocation());
        if (client.hitResult instanceof EntityHitResult existing)
            closest = Math.min(closest, eye.distanceToSqr(existing.getLocation()));
        EntityHitResult result = null;
        double visualScale = .47D * AsterionConfig.INSTANCE.minotaurScale;
        AABB search = new AABB(eye, end).inflate(8.0D);
        for (MinotaurEntity boss : client.level.getEntitiesOfClass(MinotaurEntity.class, search)) {
            AABB body = boss.getBoundingBox().inflate(2.15D * visualScale, .42D * visualScale,
                    2.15D * visualScale);
            var hit = body.contains(eye) ? java.util.Optional.of(eye) : body.clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < closest) {
                closest = eye.distanceToSqr(hit.get());
                result = new EntityHitResult(boss, hit.get());
            }
        }
        if (result != null) {
            client.hitResult = result;
            client.crosshairPickEntity = result.getEntity();
        }
    }

    public static boolean interact(Minecraft client, boolean attack) {
        if (client.player == null || client.player.isSpectator() || client.player.isUsingItem()
                || !(client.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof MinotaurEntity boss)
                || !ClientPlayNetworking.canSend(MinotaurBodyPayload.TYPE)
                || !attack && !boss.isDefeatedBoss()) return false;
        Vec3 point = hit.getLocation();
        MinotaurRemains region = region(boss, point);
        if (region.removed(boss.removedParts())) region = MinotaurRemains.next(boss.removedParts());
        ClientPlayNetworking.send(new MinotaurBodyPayload(boss.getId(), point, attack,
                region == null ? -1 : region.ordinal()));
        client.player.swing(InteractionHand.MAIN_HAND);
        if (attack) client.player.resetAttackStrengthTicker();
        return true;
    }

    private static MinotaurRemains region(MinotaurEntity boss, Vec3 point) {
        Vec3 relative = point.subtract(boss.position());
        double yaw = Math.toRadians(-boss.yBodyRot);
        double localX = relative.x * Math.cos(yaw) - relative.z * Math.sin(yaw);
        double fraction = relative.y / Math.max(.1D, boss.getBbHeight());
        if (fraction > .73D) return MinotaurRemains.HEAD;
        if (fraction < .42D) return localX >= 0 ? MinotaurRemains.LEFT_LEG : MinotaurRemains.RIGHT_LEG;
        if (Math.abs(localX) > boss.getBbWidth() * .42D)
            return localX >= 0 ? MinotaurRemains.LEFT_ARM : MinotaurRemains.RIGHT_ARM;
        return MinotaurRemains.TORSO;
    }
}

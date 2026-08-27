package net.krodark.labyrinth.network.ragdoll;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.labyrinth.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class RagdollServerNetworking {
    private static final Map<String, Long> LAST_POSE = new HashMap<>();

    private RagdollServerNetworking() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(RagdollFallDamagePayload.TYPE, (payload, context) ->
                context.server().execute(() -> applyFallDamage(context.player(), payload.damage())));
        ServerPlayNetworking.registerGlobalReceiver(TumbleExitPayload.TYPE, (payload, context) ->
                context.server().execute(() -> exitTumble(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RagdollKillPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var target = context.player().level().getEntity(payload.entityId());
                    if (target instanceof LivingEntity living
                            && !(living instanceof Player)
                            && living.isAlive()
                            && context.player().distanceToSqr(living) <= 64 * 64
                            && context.player().hasLineOfSight(living)) {
                        living.kill(context.player().level());
                    }
                }));
        ServerPlayNetworking.registerGlobalReceiver(RagdollEntityImpactPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var player = context.player();
                    var target = player.level().getEntity(payload.entityId());
                    boolean invalidTarget = target == null || target == player || player.distanceToSqr(target) > 81;
                    boolean invalidImpulse = !Float.isFinite(payload.x())
                            || !Float.isFinite(payload.y())
                            || !Float.isFinite(payload.z());
                    if (invalidTarget || invalidImpulse) {
                        return;
                    }

                    Vec3 impulse = new Vec3(payload.x(), payload.y(), payload.z());
                    if (impulse.lengthSqr() > .16) {
                        impulse = impulse.normalize().scale(.4);
                    }
                    target.push(impulse.x, Math.max(-.08, impulse.y), impulse.z);
                }));
        ServerPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE, (payload, context) ->
                context.server().execute(() -> relayPose(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RagdollBlockImpactPayload.TYPE, (payload, context) -> { });
        ServerPlayNetworking.registerGlobalReceiver(RagdollArmorImpactPayload.TYPE, (payload, context) -> { });
    }

    private static void applyFallDamage(ServerPlayer player, float damage) {
        if (WorldGenerator.hasFallProtection(player) || !Float.isFinite(damage) || damage < .5f) {
            return;
        }
        player.hurtServer(player.level(), player.damageSources().fall(), Math.min(20, damage));
    }

    private static void exitTumble(ServerPlayer player, TumbleExitPayload payload) {
        if (WorldGenerator.isElectrified(player)) return;
        Vec3 target = new Vec3(payload.x(), payload.y(), payload.z());
        boolean invalidPosition = !finite(target) || player.position().distanceToSqr(target) > 1024;
        boolean invalidVelocity = !Double.isFinite(payload.vx())
                || !Double.isFinite(payload.vy())
                || !Double.isFinite(payload.vz());
        if (invalidPosition || invalidVelocity) {
            return;
        }

        BlockPos pos = BlockPos.containing(target);
        var destination = player.getBoundingBox().move(target.subtract(player.position())).deflate(.001);
        if (!player.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                || !player.level().noCollision(player, destination)) {
            return;
        }

        player.teleportTo(target.x, target.y, target.z);
        Vec3 velocity = new Vec3(payload.vx(), payload.vy(), payload.vz());
        if (velocity.lengthSqr() > 16) {
            velocity = velocity.normalize().scale(4);
        }
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        if (ServerPlayNetworking.canSend(player, RagdollAuthorityPayload.TYPE)) {
            ServerPlayNetworking.send(player, new RagdollAuthorityPayload(player.position(), velocity,
                    player.level().getGameTime()));
        }
    }

    private static void relayPose(ServerPlayer sender, RagdollPosePayload payload) {
        if (payload.parts().isEmpty() || payload.parts().size() > 16) {
            return;
        }
        long now = sender.level().getGameTime();
        if (LAST_POSE.size() > 256) {
            LAST_POSE.entrySet().removeIf(entry -> now - entry.getValue() > 200);
        }
        String key = sender.getUUID() + ":" + payload.entityId();
        if (now - LAST_POSE.getOrDefault(key, -1000L) < 2) {
            return;
        }
        LAST_POSE.put(key, now);
        var tracked = sender.level().getEntity(payload.entityId());
        if (tracked instanceof ServerPlayer && tracked != sender) {
            return;
        }
        RagdollPosePayload.Part root = payload.parts().getFirst();
        Vec3 center = new Vec3(root.x(), root.y(), root.z());
        if (!finite(center) || sender.distanceToSqr(center) > 96 * 96) {
            return;
        }

        for (ServerPlayer viewer : sender.level().players()) {
            if (viewer != sender && viewer.distanceToSqr(center) < 96 * 96
                    && ServerPlayNetworking.canSend(viewer, RagdollPosePayload.TYPE)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}

package net.krodark.asterion.network.ragdoll;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RagdollServerNetworking {
    private static final Map<String, Long> LAST_POSE = new HashMap<>();
    private static final Map<UUID, Long> ACTIVE_RAGDOLLS = new HashMap<>();
    private static final Map<UUID, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> RAGDOLL_LEVELS = new HashMap<>();
    private static final Map<UUID, Integer> SCRIPTED_THROW_DAMAGE = new HashMap<>();

    public static void suppressThrowFallDamage(ServerPlayer player, int ticks) {
        int now = player.level().getServer().getTickCount();
        SCRIPTED_THROW_DAMAGE.entrySet().removeIf(entry -> entry.getValue() < now);
        SCRIPTED_THROW_DAMAGE.put(player.getUUID(), now + ticks);
    }

    private RagdollServerNetworking() {
    }

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SCRIPTED_THROW_DAMAGE.clear(); ACTIVE_RAGDOLLS.clear(); RAGDOLL_LEVELS.clear(); LAST_POSE.clear();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID id : java.util.List.copyOf(ACTIVE_RAGDOLLS.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) { ACTIVE_RAGDOLLS.remove(id); RAGDOLL_LEVELS.remove(id); }
                else if (!player.isAlive() || !player.level().dimension().equals(RAGDOLL_LEVELS.get(id))
                        || ACTIVE_RAGDOLLS.get(id) < server.getTickCount()) finishRagdoll(player);
            }
        });
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof ServerPlayer player && isRagdolled(player)) sendState(viewer, player, true);
        });
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.STOP_TRACKING.register((entity, viewer) -> {
            if (entity instanceof ServerPlayer player) sendState(viewer, player, false);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> finishRagdoll(handler.getPlayer()));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> finishRagdoll(oldPlayer));
        ServerPlayNetworking.registerGlobalReceiver(RagdollFallDamagePayload.TYPE, (payload, context) ->
                context.server().execute(() -> applyFallDamage(context.player(), payload.damage())));
        ServerPlayNetworking.registerGlobalReceiver(TumbleExitPayload.TYPE, (payload, context) ->
                context.server().execute(() -> exitTumble(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(RagdollKillPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var target = context.player().level().getEntity(payload.entityId());
                    if (target instanceof LivingEntity living
                            && !(living instanceof Player)
                            && !(living instanceof MinotaurEntity)
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
        if (SCRIPTED_THROW_DAMAGE.getOrDefault(player.getUUID(), -1) >= player.level().getServer().getTickCount()) return;
        if (MinotaurEntity.controlsPlayer(player)) return;
        if (WorldGenerator.hasFallProtection(player) || !Float.isFinite(damage) || damage < .5f) {
            return;
        }
        player.hurtServer(player.level(), player.damageSources().fall(), Math.min(20, damage));
    }

    private static void exitTumble(ServerPlayer player, TumbleExitPayload payload) {
        if (!player.isAlive() || player.isSpectator()) return;
        if (MinotaurEntity.controlsPlayer(player)) return;
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

        // Following a ragdoll is movement, not a teleport/stand-up on every frame.
        if (payload.finished()) player.teleportTo(target.x, target.y, target.z);
        else player.setPos(target);
        Vec3 velocity = new Vec3(payload.vx(), payload.vy(), payload.vz());
        if (velocity.lengthSqr() > 16) {
            velocity = velocity.normalize().scale(4);
        }
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        if (payload.finished()) finishRagdoll(player);
        else markRagdolled(player, 60);
        if (payload.finished() && ServerPlayNetworking.canSend(player, RagdollAuthorityPayload.TYPE)) {
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
        if (!(tracked instanceof LivingEntity) || tracked instanceof MinotaurEntity) return;
        if (tracked instanceof ServerPlayer && tracked != sender) {
            return;
        }
        RagdollPosePayload.Part root = payload.parts().getFirst();
        Vec3 center = new Vec3(root.x(), root.y(), root.z());
        if (!finite(center) || sender.distanceToSqr(center) > 96 * 96) {
            return;
        }
        // Validate every body transform before it can reach another client's renderer.
        for (var part : payload.parts()) {
            Vec3 point = new Vec3(part.x(), part.y(), part.z());
            Vec3 velocity = new Vec3(part.vx(), part.vy(), part.vz());
            if (!finite(point) || !finite(velocity) || point.distanceToSqr(center) > 64
                    || velocity.lengthSqr() > 256 || !Float.isFinite(part.qx()) || !Float.isFinite(part.qy())
                    || !Float.isFinite(part.qz()) || !Float.isFinite(part.qw())) return;
        }
        if (tracked == sender) {
            if (!sender.isAlive() || sender.isSpectator()) return;
            markRagdolled(sender, 60);
        } else if (tracked.isAlive() || sender.distanceToSqr(tracked) > 48 * 48) return;

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

    public static void markRagdolled(ServerPlayer player, int ticks) {
        long expires = player.level().getServer().getTickCount() + Math.max(1, ticks);
        boolean started = !isRagdolled(player);
        ACTIVE_RAGDOLLS.merge(player.getUUID(), expires, Math::max);
        RAGDOLL_LEVELS.put(player.getUUID(), player.level().dimension());
        if (started) for (ServerPlayer viewer : player.level().players()) sendState(viewer, player, true);
    }

    public static boolean isRagdolled(ServerPlayer player) {
        long expires = ACTIVE_RAGDOLLS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        return expires >= player.level().getServer().getTickCount();
    }

    public static void finishRagdoll(ServerPlayer player) {
        if (ACTIVE_RAGDOLLS.remove(player.getUUID()) == null) return;
        RAGDOLL_LEVELS.remove(player.getUUID());
        LAST_POSE.keySet().removeIf(key -> key.startsWith(player.getUUID() + ":"));
        for (ServerPlayer viewer : player.level().getServer().getPlayerList().getPlayers()) sendState(viewer, player, false);
    }

    private static void sendState(ServerPlayer viewer, ServerPlayer owner, boolean active) {
        if (ServerPlayNetworking.canSend(viewer, RagdollStatePayload.TYPE))
            ServerPlayNetworking.send(viewer, new RagdollStatePayload(owner.getId(), owner.getUUID(), active));
    }

    public static void forceAuthority(ServerPlayer player, Vec3 velocity) {
        if (ServerPlayNetworking.canSend(player, RagdollAuthorityPayload.TYPE))
            ServerPlayNetworking.send(player, new RagdollAuthorityPayload(player.position(), velocity,
                    player.level().getGameTime()));
    }
}

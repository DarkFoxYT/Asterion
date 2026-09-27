package net.krodark.asterion.game;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.BossEncounterResetPayload;
import net.krodark.asterion.network.DimensionTransitionPayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ArenaDeathRecovery {
    private ArenaDeathRecovery() { }
    private static final Map<UUID, Recovery> pending = new HashMap<>();
    private record Recovery(ServerLevel level, BlockPos origin, Vec3 gatePosition,
                            boolean invulnerable, boolean noGravity, boolean minotaur, long at) { }

    public static boolean isRecovering(ServerPlayer player) { return pending.containsKey(player.getUUID()); }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Recovery recovery = pending.get(handler.player.getUUID());
            if (recovery != null) {
                handler.player.setInvulnerable(recovery.invulnerable());
                handler.player.setNoGravity(recovery.noGravity());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Recovery recovery = entry.getValue();
                if (server.getTickCount() < recovery.at()) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                // A wipe rebuilds in bounded chunk batches; wait for safe terrain.
                if (player.level() == recovery.level() && recovery.minotaur()
                        && !BossArenaEncounter.isSealed(recovery.level()) && !WorldGenerator.isBossArenaReady()
                        && server.getTickCount() < recovery.at() + 1200) continue;
                BlockPos safe = null;
                if (player.level() == recovery.level() && recovery.gatePosition() != null && !player.isSpectator()) {
                    try {
                        safe = WorldGenerator.resolveSafeRespawn(recovery.level(), BlockPos.containing(recovery.gatePosition()));
                    } catch (IllegalStateException notReady) {
                        // Do not drop protection or crash the tick while the exit terrain is unavailable.
                        continue;
                    }
                }
                iterator.remove();
                player.setInvulnerable(recovery.invulnerable());
                player.setNoGravity(recovery.noGravity());
                if (player.level() != recovery.level() || player.isSpectator()) continue;
                releaseBossGrip(player, recovery.level());
                if (recovery.gatePosition() != null) {
                    player.teleportTo(recovery.level(), safe.getX() + .5, safe.getY(), safe.getZ() + .5,
                            Set.of(), 180, 0, true);
                } else WorldGenerator.respawnAtRune(player, recovery.origin());
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.clearFire();
                player.invulnerableTime = 80;
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || player.getHealth() > 0
                    || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Asterion.ASTERION_LEVEL)) return true;
            if (isRecovering(player)) { player.setHealth(1); return false; }
            boolean minotaur = BossArenaEncounter.isParticipant(player);
            CursedBrazierEntity brazier = null;
            if (!minotaur) for (var candidate : level.getAllEntities())
                if (candidate instanceof CursedBrazierEntity boss && boss.isParticipant(player)) { brazier = boss; break; }
            if (!minotaur && brazier == null) return true;

            Vec3 gate = minotaur ? BossArenaEncounter.recoveryPosition(player) : brazier.recoveryPosition();
            if (minotaur) BossArenaEncounter.releaseMovementLock(player);
            pending.put(player.getUUID(), new Recovery(level, player.blockPosition().immutable(), gate,
                    player.isInvulnerable(), player.isNoGravity(), minotaur, level.getServer().getTickCount() + 20));
            boolean wipe;
            if (minotaur) wipe = !BossArenaEncounter.hasSurvivingParticipant(player);
            else wipe = !brazier.hasSurvivingParticipant(player);
            releaseBossGrip(player, level);
            if (wipe) {
                for (ServerPlayer member : List.copyOf(level.players()))
                    if (minotaur ? BossArenaEncounter.isParticipant(member) : brazier.isParticipant(member))
                        EncounterKeyRecovery.restoreConsumed(member, minotaur ? Asterion.MINOTAUR_KEY : GameplayContent.CURSED_BRAZIER_KEY);
                if (minotaur) WorldGenerator.resetBossEncounterAfterDeath(player);
                else brazier.resetAfterPlayerDeath(level);
            }
            if (!wipe) {
                if (minotaur) BossArenaEncounter.eliminate(player);
                else brazier.eliminate(player);
            }
            // Delay healing until relocation so the death screen does not show a full-health player.
            player.setHealth(1);
            player.setInvulnerable(true);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.clearFire();
            if (ServerPlayNetworking.canSend(player, BossEncounterResetPayload.TYPE))
                ServerPlayNetworking.send(player, BossEncounterResetPayload.INSTANCE);
            if (ServerPlayNetworking.canSend(player, DimensionTransitionPayload.TYPE))
                ServerPlayNetworking.send(player, new DimensionTransitionPayload(20, wipe ? 120 : 40, 1));
            return false;
        });
    }

    private static void releaseBossGrip(ServerPlayer player, ServerLevel level) {
        for (var entity : level.getAllEntities())
            if (entity instanceof MinotaurEntity boss) boss.withdrawParticipant(player);
        RagdollServerNetworking.finishRagdoll(player);
        player.stopRiding();
    }
}

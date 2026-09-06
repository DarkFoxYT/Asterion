package net.krodark.asterion.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

 
public final class ArenaDeathRecovery {
    private ArenaDeathRecovery() { }
    private static final java.util.List<Recovery> pending = new java.util.ArrayList<>();
    private record Member(java.util.UUID id, boolean invulnerable) { }
    private record Recovery(ServerLevel level, BlockPos origin, java.util.List<Member> members, long at) { }

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = pending.iterator();
            while (iterator.hasNext()) {
                Recovery recovery = iterator.next();
                if (server.getTickCount() < recovery.at()) continue;
                iterator.remove();
                net.minecraft.world.phys.Vec3 destination = null;
                for (Member member : recovery.members()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(member.id());
                    if (player == null) continue;
                    player.setInvulnerable(member.invulnerable());
                    if (player.level() != recovery.level() || player.isSpectator()) continue;
                    for (var entity : recovery.level().getAllEntities())
                        if (entity instanceof net.krodark.asterion.entity.MinotaurEntity boss) boss.withdrawParticipant(player);
                    net.krodark.asterion.worldgen.BossArenaEncounter.releasePlayer(player);
                    net.krodark.asterion.network.ragdoll.RagdollServerNetworking.finishRagdoll(player);
                    player.stopRiding();
                    if (destination == null) {
                        WorldGenerator.respawnAtRune(player, recovery.origin());
                        destination = player.position();
                    } else player.teleportTo(recovery.level(), destination.x, destination.y, destination.z,
                            java.util.Set.of(), player.getYRot(), 0, true);
                    player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    player.resetFallDistance();
                    player.clearFire();
                    player.invulnerableTime = 80;
                }
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)-> {
            if(!(entity instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Asterion.ASTERION_LEVEL))return true;
            BlockPos deathPosition=player.blockPosition().immutable();
            boolean minotaurArena=net.krodark.asterion.worldgen.BossArenaEncounter.isParticipant(player);
            net.krodark.asterion.entity.CursedBrazierEntity brazier = null;
            for (var candidate : level.getAllEntities())
                if (candidate instanceof net.krodark.asterion.entity.CursedBrazierEntity boss && boss.isParticipant(player)) {
                    brazier = boss; break;
                }
            boolean cursedArena=brazier != null;
            if(!minotaurArena&&!cursedArena)return true;

             
             
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.clearFire();
            player.invulnerableTime=60;

            if(minotaurArena)
                EncounterKeyRecovery.restoreConsumed(player,Asterion.MINOTAUR_KEY);
            if(cursedArena)
                EncounterKeyRecovery.restoreConsumed(player,GameplayContent.CURSED_BRAZIER_KEY);

            if (pending.stream().anyMatch(group -> group.members().stream().anyMatch(member -> member.id().equals(player.getUUID())))) return false;
            var members = new java.util.ArrayList<Member>();
            for (ServerPlayer member : java.util.List.copyOf(level.players())) {
                if (!member.isAlive() || member.isSpectator()) continue;
                if (!(minotaurArena ? net.krodark.asterion.worldgen.BossArenaEncounter.isParticipant(member) : brazier.isParticipant(member))) continue;
                members.add(new Member(member.getUUID(), member.isInvulnerable()));
                member.setInvulnerable(true);
                member.stopRiding();
                net.krodark.asterion.network.ragdoll.RagdollServerNetworking.finishRagdoll(member);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(member, net.krodark.asterion.network.BossEncounterResetPayload.TYPE))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(member, net.krodark.asterion.network.BossEncounterResetPayload.INSTANCE);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(member, net.krodark.asterion.network.DimensionTransitionPayload.TYPE))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(member,
                            new net.krodark.asterion.network.DimensionTransitionPayload(20, 40, member == player ? 1 : 2));
            }
            pending.add(new Recovery(level, deathPosition, java.util.List.copyOf(members), level.getServer().getTickCount() + 20));
            return false;
        });
    }
}

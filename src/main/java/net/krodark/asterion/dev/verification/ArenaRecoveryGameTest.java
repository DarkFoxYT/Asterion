package net.krodark.asterion.dev.verification;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.game.ArenaDeathRecovery;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class ArenaRecoveryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(maze, .5, 100, .5, Set.of(), 0, 0, true);
            });
            waitReady(context, world.getServer());
            var gate = new java.util.concurrent.atomic.AtomicReference<Vec3>();
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                Vec3 entrance = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE)).add(0, 0, 3);
                player.teleportTo(maze, entrance.x, entrance.y, entrance.z, Set.of(), 180, 0, true);
                var boss = MinotaurEntity.activateCenterBoss(maze, player, null, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                boss.setNoAi(true);
                BossArenaEncounter.begin(maze, player, boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                BossArenaEncounter.releaseMovementLock(player);
                gate.set(BossArenaEncounter.recoveryPosition(player));
                var teammate = new ServerPlayer(server, maze, new GameProfile(UUID.randomUUID(), "RecoveryTeammate"), ClientInformation.createDefault());
                teammate.connection = player.connection;
                teammate.setGameMode(GameType.SURVIVAL);
                teammate.setPos(gate.get());
                maze.addNewPlayer(teammate);
                BossArenaEncounter.begin(maze, teammate, boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                BossArenaEncounter.releaseMovementLock(teammate);
                // Synthetic teammate shares a connection; set its position independently of that connection's player.
                teammate.setPos(Vec3.atBottomCenterOf(MinotaurArenaEntrances.gate(MinotaurArenaEntrances.PLAYER_ENTRANCE)).add(0, 0, -10));
                try {
                    Vec3 fightingPosition = teammate.position();
                    teammate.setPos(gate.get());
                    check(!BossArenaEncounter.hasSurvivingParticipant(player), "Outside teammate counted as fighter");
                    teammate.setPos(fightingPosition.add(0, 50, 0));
                    check(!BossArenaEncounter.hasSurvivingParticipant(player), "Other layer counted as fighter");
                    teammate.setPos(fightingPosition);
                    check(BossArenaEncounter.hasSurvivingParticipant(player), "Inside teammate was excluded at " + teammate.position() + " participant=" + BossArenaEncounter.isParticipant(teammate) + " alive=" + teammate.isAlive() + " mode=" + teammate.gameMode.getGameModeForPlayer());
                    player.setHealth(10);
                    check(ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, maze.damageSources().generic(), 1), "Nonlethal notification was intercepted");
                    check(!ArenaDeathRecovery.isRecovering(player), "Living player received death recovery");
                    float bossHealth = boss.getHealth(), teammateHealth = teammate.getHealth();
                    die(player);
                    check(!boss.isRemoved() && boss.getHealth() == bossHealth && BossArenaEncounter.isSealed(maze), "One death reset a surviving party's boss");
                    check(!ArenaDeathRecovery.isRecovering(teammate) && teammate.getHealth() == teammateHealth && !teammate.isInvulnerable(), "Surviving teammate was pulled into recovery");
                    check(!BossArenaEncounter.isParticipant(player), "Defeated player remained in attempt");
                    BossArenaEncounter.begin(maze, player, boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                    check(!BossArenaEncounter.isParticipant(player), "Defeated player rejoined active attempt");
                    check(!WorldGenerator.resetBossEncounterAfterDeath(player), "Fallback death path reset a surviving party");
                    maze.setBlock(new BlockPos(-5, AuthoredCatacombs.ARENA_FLOOR_Y, -25), Blocks.AIR.defaultBlockState(), 18);
                    die(teammate);
                    check(boss.isRemoved() && !BossArenaEncounter.isSealed(maze), "Full wipe did not retire the old boss");
                    check(!WorldGenerator.isBossArenaReady(), "Wipe skipped arena rebuild");
                } finally { teammate.discard(); }
            });
            waitReady(context, world.getServer());
            context.waitTicks(25);
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                check(!ArenaDeathRecovery.isRecovering(player) && player.getHealth() == player.getMaxHealth(), "Recovery did not finish");
                check(player.position().distanceToSqr(gate.get()) < 4, "Respawn missed the exterior entrance: " + player.position());
                check(!player.isInvulnerable() && !player.isNoGravity(), "Recovery leaked invulnerability or flight");
                check(!maze.getBlockState(new BlockPos(-5, AuthoredCatacombs.ARENA_FLOOR_Y, -25)).isAir(), "Arena pillar was not rebuilt");
                var boss = MinotaurEntity.activateCenterBoss(maze, player, null, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                boss.setNoAi(true);
                BossArenaEncounter.begin(maze, player, boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                BossArenaEncounter.releaseMovementLock(player);
                die(player);
                check(boss.isRemoved() && !WorldGenerator.isBossArenaReady(), "Solo death/second round did not reset");
            });
            waitReady(context, world.getServer());
            context.waitTicks(25);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                check(!ArenaDeathRecovery.isRecovering(player), "Second recovery stuck");
                var boss = MinotaurEntity.activateCenterBoss(maze, player, null, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                boss.setNoAi(true);
                BossArenaEncounter.begin(maze, player, boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
                BossArenaEncounter.releaseMovementLock(player);
                player.teleportTo(maze, 300, 150, 300, Set.of(), 0, 0, true);
                for (int i = 0; i < 99; i++) BossArenaEncounter.tick(maze);
                check(!boss.isRemoved(), "Empty arena grace period skipped");
                BossArenaEncounter.tick(maze);
                check(boss.isRemoved() && !BossArenaEncounter.isSealed(maze), "Abandoned Minotaur encounter stayed active");
            });
            Asterion.LOGGER.info("PASS: nonlethal guard, surviving teammate unchanged, full wipe, gate recovery, pillar repair and repeat solo reset");
        }
    }

    private static void die(ServerPlayer player) {
        player.setHealth(0);
        check(!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, player.level().damageSources().generic(), 100), "Arena death escaped recovery");
    }
    private static void waitReady(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        for (int i = 0; i < 40 && !ready.get(); i++) {
            context.waitTicks(40);
            server.runOnServer(mc -> ready.set(WorldGenerator.isBossArenaReady()));
        }
        check(ready.get(), "Arena preparation/reset timed out");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.Direction;

public final class MinotaurEntranceVisualGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> WorldGenerator.ensureBossArenaReady(mc.getLevel(Asterion.ASTERION_LEVEL)));
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 " + (AuthoredCatacombs.ARENA_FLOOR_Y + 1) + " 40.5 180 0");
            context.waitTicks(40);
            var id = new java.util.concurrent.atomic.AtomicReference<java.util.UUID>();
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                var boss = MinotaurEntity.activateCenterBoss(level, player, null, Direction.SOUTH);
                id.set(boss.getUUID());
                BossArenaEncounter.begin(level, player, boss, Direction.SOUTH);
                for (int tick = MinotaurAnimationTiming.ENTRY_BREAK_TICK; tick <= MinotaurAnimationTiming.ENTRY_END_TICK; tick++)
                    if (MinotaurEntranceMotion.point(tick, boss.getBbWidth()).y != MinotaurEntranceMotion.point(0, boss.getBbWidth()).y)
                        throw new AssertionError("Entrance left the ground");
            });
            int previous = 0;
            for (int tick : new int[]{150, 173, 195, 240, 270}) {
                context.waitTicks(tick - previous); previous = tick;
                server.runOnServer(mc -> {
                    var b = (MinotaurEntity)mc.getLevel(Asterion.ASTERION_LEVEL).getEntity(id.get());
                    Asterion.LOGGER.info("INTRO CHECK {} server={} phase={} entry={} health={}", tick, b.position(), b.animationState(), b.doorEntryTicks(), b.getHealth());
                });
                context.runOnClient(c -> {
                    boolean found = false;
                    for (var e : c.level.entitiesForRendering()) if (e instanceof MinotaurEntity b && b.getUUID().equals(id.get())) found = true;
                    if (!found) throw new AssertionError("Cinematic boss missing from client at " + tick);
                });
                context.takeScreenshot("minotaur-intro-" + tick);
            }
            context.waitTicks(BossArenaEncounter.INTRO_TICKS + 5 - previous);
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                var boss = (MinotaurEntity)level.getEntity(id.get());
                if (boss == null || boss.doorEntryTicks() != 0 || boss.isNoGravity())
                    throw new AssertionError("Entrance did not restore normal boss physics");
                if (BossArenaEncounter.isMovementLocked(mc.getPlayerList().getPlayers().getFirst()))
                    throw new AssertionError("Player controls remained locked");
            });
            Asterion.LOGGER.info("PASS: grounded breach path, full cinematic playback, restored physics and player controls");
        }
    }
}

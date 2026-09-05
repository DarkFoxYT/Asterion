package net.krodark.asterion.dev.verification;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.MazeObjectiveOverlay;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class MinotaurGrabGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var bossRef = new AtomicReference<MinotaurEntity>();
            var barRef = new AtomicReference<net.minecraft.server.level.ServerBossEvent>();
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 1000, 200, 1000, Set.of(), 0, 0, true);
                player.setInvulnerable(true);
                player.setNoGravity(true);
                for (BlockPos p : BlockPos.betweenClosed(980, 199, 980, 1020, 199, 1020))
                    level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
            });
            context.waitTicks(30);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = Asterion.MINOTAUR.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                check(boss != null, "Debug boss missing");
                boss.setPos(1000, 200, 1004);
                boss.beginDebug(player);
                boss.setDebugRunning(false);
                player.level().addFreshEntity(boss);
                bossRef.set(boss);
                var bar = new net.minecraft.server.level.ServerBossEvent(
                        java.util.UUID.randomUUID(),
                        net.minecraft.network.chat.Component.literal("Grab test"),
                        net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
                bar.addPlayer(player);
                barRef.set(bar);
                player.teleportTo(boss.getX() + 2, boss.getY() + 2, boss.getZ());
            });
            server.runOnServer(mc -> check(bossRef.get().forceDebugAttack(
                    mc.getPlayerList().getPlayers().getFirst(), "grab"), "Grab did not start"));
            context.waitTicks(20);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(bossRef.get().heldPlayerId() == player.getId(), "Player was not held");
                check(RagdollServerNetworking.isRagdolled(player), "Grab did not start server ragdoll");
                try {
                    var exit = RagdollServerNetworking.class.getDeclaredMethod("exitTumble",
                            net.minecraft.server.level.ServerPlayer.class, net.krodark.asterion.network.ragdoll.TumbleExitPayload.class);
                    exit.setAccessible(true);
                    Vec3 position = player.position();
                    exit.invoke(null, player, new net.krodark.asterion.network.ragdoll.TumbleExitPayload(
                            position.x + 2, position.y, position.z, 0, 0, 0));
                    check(player.position().equals(position) && RagdollServerNetworking.isRagdolled(player),
                            "Client recovery escaped the grab");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            context.runOnClient(client -> {
                var engine = DismembermentEngine.INSTANCE;
                check(engine.isPlayerTumbling(client.player.getId()), "Held player is still using vanilla rendering");
                try {
                    var pieces = DismembermentEngine.class.getDeclaredField("pieces");
                    pieces.setAccessible(true);
                    check(((java.util.List<?>)pieces.get(engine)).size() >= 6, "Held player is missing body parts");
                    var method = MazeObjectiveOverlay.class.getDeclaredMethod("bossFightActive", net.minecraft.client.Minecraft.class);
                    method.setAccessible(true);
                    check((boolean)method.invoke(null, client), "Objectives did not detect the boss fight");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(2);
            context.takeScreenshot("grab-ragdoll-third-person");
            AtomicReference<Vec3> start = new AtomicReference<>();
            server.runOnServer(mc -> start.set(mc.getPlayerList().getPlayers().getFirst().position()));
            context.waitTicks(35);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(bossRef.get().heldPlayerId() == -1, "Throw left the hand attached");
                check(RagdollServerNetworking.isRagdolled(player), "Throw ended ragdoll state");
                check(player.position().distanceTo(start.get()) > 5, "Throw did not move the player");
                barRef.get().removeAllPlayers();
            });
            Asterion.LOGGER.info("PASS: boss objectives hidden, immediate full-body grab ragdoll, release and authoritative throw");
        }
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.krodark.asterion.network.ragdoll.TumbleExitPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

public final class RespawnSafetyGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("execute in asterion:asterion_dimension run tp @a 202.5 121 202.5 0 0");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                for (int x = 196; x <= 207; x++) for (int z = 196; z <= 207; z++) {
                    level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 121; y <= 124; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                BlockPos checkpoint = new BlockPos(200, 121, 200);
                AsterionWorldState.get(level).setRuneCheckpoint(player.getUUID(), checkpoint);
                level.setBlock(checkpoint, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(checkpoint.above(), Blocks.STONE.defaultBlockState(), 3);
            });
            context.waitTicks(70);
            context.runOnClient(client -> {
                DismembermentEngine.INSTANCE.togglePlayerTumble(client);
                check(DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId()), "Test player did not enter ragdoll");
            });
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                RagdollServerNetworking.markRagdolled(player, 80);
                player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
            });
            context.waitTicks(5);
            context.runOnClient(client -> client.player.respawn());
            context.waitTicks(10);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(player.isAlive(), "Respawned player died");
                check(player.level().dimension().equals(Asterion.ASTERION_LEVEL), "Respawn left the maze");
                check(player.level().noCollision(player), "Respawn intersects a wall");
                check(player.blockPosition().distSqr(new BlockPos(200,121,200)) < 100, "Blocked checkpoint caused distant generation");
                check(!RagdollServerNetworking.isRagdolled(player), "Server retained ragdoll state");
                var position = player.position();
                var exit = RagdollServerNetworking.class.getDeclaredMethod("exitTumble", ServerPlayer.class, TumbleExitPayload.class);
                exit.setAccessible(true);
                exit.invoke(null, player, new TumbleExitPayload(position.x + 1, position.y, position.z, 1, 0, 0));
                check(player.position().equals(position), "Stale get-up packet moved the respawned player");
            });
            context.runOnClient(client -> {
                check(!DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId()), "Respawn retained client ragdoll");
                check(!DismembermentEngine.INSTANCE.isRagdolled(client.player.getId()), "Respawn retained corpse geometry");
            });
            Asterion.LOGGER.info("PASS: actual death while ragdolled, blocked checkpoint recovery, collision-free respawn and stale get-up rejection");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

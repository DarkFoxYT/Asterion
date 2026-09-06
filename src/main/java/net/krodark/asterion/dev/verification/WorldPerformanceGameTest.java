package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.AuthoredForge;

public final class WorldPerformanceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        long start = System.nanoTime();
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(60);
            Asterion.LOGGER.info("PERF: world created and 60 client ticks completed in {} ms", (System.nanoTime() - start) / 1_000_000);
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                Asterion.LOGGER.info("PERF: empty maze loaded chunks={}", maze.getChunkSource().getLoadedChunksCount());
                net.krodark.asterion.worldgen.ZoneRunePlacement.tick(maze);
                if (maze.getChunkSource().getChunkNow(-4, -4) != null)
                    throw new AssertionError("Empty dimension generated the remote arena during world creation");
                try {
                    var create = AuthoredForge.class.getDeclaredMethod("createLayout", net.minecraft.server.level.ServerLevel.class, int.class);
                    create.setAccessible(true);
                    for (int variant = 0; variant < 3; variant++) {
                        long begin = System.nanoTime();
                        create.invoke(null, maze, variant);
                        Asterion.LOGGER.info("PERF: Forge variant {} computed in {} ms", variant, (System.nanoTime() - begin) / 1_000_000);
                    }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(maze, .5, 200, .5, java.util.Set.of(), 0, 0, true);
            });
            long arrival = System.nanoTime();
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            for (int attempt = 0; attempt < 20 && !ready.get(); attempt++) {
                context.waitTicks(40);
                world.getServer().runOnServer(server -> ready.set(net.krodark.asterion.WorldGenerator.isBossArenaReady()));
            }
            if (!ready.get()) throw new AssertionError("Asynchronous arena preparation never completed");
            world.getServer().runOnServer(server -> {
                if (!server.getPlayerList().getPlayers().getFirst().connection.hasClientLoaded())
                    throw new AssertionError("Player did not finish joining the maze");
            });
            Asterion.LOGGER.info("PASS: empty maze stayed deferred; player joined and complete arena prepared in {} ms", (System.nanoTime() - arrival) / 1_000_000);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.overworld(), .5, 100, .5, java.util.Set.of(), 0, 0, true);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.noPhysics = false;
                try {
                    var begin = net.krodark.asterion.WorldGenerator.class.getDeclaredMethod("beginTransition",
                            net.minecraft.server.level.ServerPlayer.class, net.minecraft.server.level.ServerLevel.class);
                    begin.setAccessible(true);
                    begin.invoke(null, player, server.getLevel(Asterion.ASTERION_LEVEL));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            ready.set(false);
            for (int attempt = 0; attempt < 30 && !ready.get(); attempt++) {
                context.waitTicks(40);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    ready.set(player.level().dimension().equals(Asterion.ASTERION_LEVEL)
                            && player.connection.hasClientLoaded() && !player.noPhysics);
                });
            }
            if (!ready.get()) throw new AssertionError("Portal did not finish asynchronous generation and client acknowledgement");
            Asterion.LOGGER.info("PASS: portal generated a fresh destination, transferred the player and released the transition lock");
        }
    }
}

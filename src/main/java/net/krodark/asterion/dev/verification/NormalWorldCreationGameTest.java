package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;

/** Measures real survival terrain and spawn search, which flat test worlds skip. */
public final class NormalWorldCreationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        // Start cold with the user's world that spent 66 seconds selecting its spawn.
        for (long seed : new long[]{-1950657107712358405L, 20260906L, 20260907L}) {
            long start = System.nanoTime();
            try (var world = context.worldBuilder().setUseConsistentSettings(false)
                    .adjustSettings(settings -> settings.setSeed(Long.toString(seed))).create()) {
                context.waitTicks(60);
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    if (!player.connection.hasClientLoaded()) throw new AssertionError("Spawn join never completed");
                    if (server.getLevel(Asterion.ASTERION_LEVEL).getChunkSource().getLoadedChunksCount() != 0)
                        throw new AssertionError("Creating an Overworld eagerly loaded the maze");
                    Asterion.LOGGER.info("PERF NORMAL WORLD: seed={} ready+60ticks={}ms spawn={}",
                            seed, elapsed, player.blockPosition());
                });
                context.takeScreenshot("normal-world-ready-" + seed);
                context.waitTicks(200);
            }
        }
        Asterion.LOGGER.info("PASS: three normal survival worlds, spawn search, player joins and live ticks");
    }
}

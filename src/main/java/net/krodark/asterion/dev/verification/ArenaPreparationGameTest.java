package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.AuthoredCatacombs;

public final class ArenaPreparationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle());
            c.options.renderDistance().set(2);
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                if (maze.getChunkSource().getLoadedChunksCount() != 0)
                    throw new AssertionError("Unused arena generated at world startup");
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(maze, .5, 100, .5, java.util.Set.of(), 0, 0, true);
            });
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            for (int attempt = 0; attempt < 30 && !ready.get(); attempt++) {
                context.waitTicks(40);
                world.getServer().runOnServer(server -> ready.set(
                        AuthoredCatacombs.arenaComplete(server.getLevel(Asterion.ASTERION_LEVEL))));
            }
            if (!ready.get()) throw new AssertionError("Arena never completed at low render distance before any cutscene");
            net.krodark.asterion.Asterion.LOGGER.info("PASS: complete arena and approach with two-chunk render distance, no cutscene and no eager startup generation");
        }
    }
}

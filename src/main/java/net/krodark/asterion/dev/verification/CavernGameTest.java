package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.ShaleCaves;
import net.krodark.asterion.worldgen.MazeChunkGenerator;

public final class CavernGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                ShaleCavesCheck.run(level);
                long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(level, 24.5, ShaleCaves.floorY(seed, 24, 24) + 3, 24.5,
                        java.util.Set.of(), 135, 10, true);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1200, 0));
            });
            context.waitTicks(60);
            context.takeScreenshot("cavern-generation-inspection-night-vision");
        }
    }
}

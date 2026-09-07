package net.krodark.asterion.dev.verification;

import com.meekdev.amnetic.client.pipeline.PassProfiler;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import java.nio.file.Path;

public final class DimensionProfileGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
            client.options.renderDistance().set(6);
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(260);
        });
        try (var recording = new Recording(Configuration.getConfiguration("profile"));
             var world = context.worldBuilder().create()) {
            recording.start();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(server.getLevel(Asterion.ASTERION_LEVEL), 400.5, 132, 400.5,
                        java.util.Set.of(), 135, 18, true);
            });
            context.waitTicks(600);
            context.takeScreenshot("dimension-performance");
            context.runOnClient(client -> {
                Asterion.LOGGER.info("PROFILE: render size {}x{}, FPS counter {}", client.getWindow().getWidth(),
                        client.getWindow().getHeight(), client.getFps());
                PassProfiler.INSTANCE.snapshot().values().forEach(entries -> entries.forEach(entry ->
                        Asterion.LOGGER.info("PROFILE: {} {} CPU {}ms GPU {}ms", entry.stage, entry.label,
                                entry.avgMs, entry.avgGpuMs)));
            });
            recording.stop();
            Path target = Path.of("dimension-profile.jfr").toAbsolutePath();
            recording.dump(target);
            Asterion.LOGGER.info("PROFILE: saved {}", target);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}

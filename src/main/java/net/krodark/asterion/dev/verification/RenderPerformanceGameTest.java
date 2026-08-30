package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.render.ParticleCulling;
import net.minecraft.client.Minecraft;

/** Exercises the actual Amnetic integration, including fallback and shader reload. */
public final class RenderPerformanceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 0 140 0 0 0");
            long[] count = new long[1];
            context.runOnClient(client -> { count[0] = ParticleCulling.dispatches(); emit(client); });
            context.waitFor(client -> ParticleCulling.dispatches() > count[0], 300);
            context.takeScreenshot("ordered-gpu-particles");
            context.runOnClient(client -> {
                System.setProperty("asterion.disableGpuParticleCulling", "true");
                count[0] = ParticleCulling.dispatches(); emit(client);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (ParticleCulling.dispatches() != count[0]) throw new AssertionError("CPU fallback dispatched compute");
                System.clearProperty("asterion.disableGpuParticleCulling");
                com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry.INSTANCE.reloadShaders();
                emit(client);
            });
            context.waitFor(client -> ParticleCulling.dispatches() > count[0], 300);
            Asterion.LOGGER.info("PASS: Amnetic GPU particle draw, explicit CPU fallback and shader reload");
        } finally { System.clearProperty("asterion.disableGpuParticleCulling"); }
    }

    private static void emit(Minecraft client) {
        // Spread both in front of and behind the camera; keep the same sorted alpha renderer.
        var origin = client.player.position();
        for (int i = 0; i < 512; i++) {
            double x = (i % 16 - 7.5) * .5;
            double y = (i / 16 % 8) * .25;
            double z = i < 256 ? 6 : -6;
            client.particleEngine.createParticle(Asterion.GREEK_FIRE,
                    origin.x + x, origin.y + y, origin.z + z, 0, 0, 0);
        }
    }
}

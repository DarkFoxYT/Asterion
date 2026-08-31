package net.krodark.asterion.dev.verification;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.krodark.asterion.client.light.AmneticBoneEmission;

public final class VineEmissionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        PortalEmissionGameTest.Probe[] probe = new PortalEmissionGameTest.Probe[1];
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 0.5 140 0.5 0 10");
            world.getServer().runCommand("time set midnight");
            world.getServer().runCommand("setblock -2 139 5 stone");
            world.getServer().runCommand("setblock 2 141 5 stone");
            world.getServer().runCommand("setblock -2 140 5 asterion:labyrinth_vine[facing=up,end=true]");
            world.getServer().runCommand("setblock 2 140 5 asterion:labyrinth_vine[facing=down,end=true]");
            context.waitTicks(10);
            context.runOnClient(client -> {
                Bloom.settings().enabled(true).all(false).occlude(true).threshold(0);
                probe[0] = new PortalEmissionGameTest.Probe(new double[] {-1.5, 140.8, 5.5},
                        new double[] {2.5, 140.2, 5.5});
            });
            context.waitTicks(15);
            context.takeScreenshot("vine-amnetic-emission");
            context.runOnClient(client -> Asterion.LOGGER.info("Vine capture probes up={}, down={}, submissions={}",
                    probe[0].core, probe[0].halo, AmneticBoneEmission.submissions()));
            context.waitFor(client -> probe[0].visible(), 300);
            context.runOnClient(client -> { InstanceMeshRegistry.INSTANCE.reloadShaders(); probe[0].reset(); });
            context.waitFor(client -> probe[0].visible(), 300);
            world.getServer().runCommand("fill -4 139 3 4 143 3 stone");
            context.runOnClient(client -> probe[0].reset());
            context.waitFor(client -> probe[0].darkFrames >= 5, 300);
            world.getServer().runCommand("fill -4 139 3 4 143 3 air");
            context.runOnClient(client -> probe[0].reset());
            context.waitFor(client -> probe[0].visible(), 300);
            world.getServer().runCommand("setblock -2 140 5 asterion:labyrinth_vine[facing=up,end=false]");
            world.getServer().runCommand("setblock 2 140 5 asterion:labyrinth_vine[facing=down,end=false]");
            context.runOnClient(client -> probe[0].reset());
            context.waitFor(client -> probe[0].darkFrames >= 5, 300);
            Asterion.LOGGER.info("PASS: Amnetic vine emission in both orientations, reload, occlusion and middle-segment rejection");
        } finally {
            context.runOnClient(client -> {
                if (probe[0] != null) probe[0].close();
                AsterionEmissiveConfig.apply();
            });
        }
    }
}

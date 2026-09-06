package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.forge.CrucibleScreen;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.minecraft.core.BlockPos;
import java.util.Set;

public final class ForgeScreenGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        System.setProperty("asterion.verifyTextureFrames", "true");
        System.setProperty("asterion.verifiedTextureFrames", "0");
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            BlockPos pos = new BlockPos(0, 200, 0);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, .5, 201, -3, Set.of(), 0, 0, true);
                player.setNoGravity(true);
                for (var floor : BlockPos.betweenClosed(-10, 199, -10, 10, 199, 10))
                    level.setBlock(floor, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Asterion.BONESTEEL_INGOT, 4));
            });
            context.waitTicks(30);
            context.runOnClient(c -> {
                c.player.setNoGravity(true);
                c.setScreen(new CrucibleScreen(new CrucibleScreenPayload(pos, 900, 650, 20, 1200, 0, 0xD8A45A, 2, "44", 0)));
            });
            context.waitTicks(25);
            context.runOnClient(c -> ((CrucibleScreen)c.screen).update(new CrucibleScreenPayload(pos, 900, 650, 20, 1200, 0, 0xD8A45A, 2, "44", 0)));
            context.takeScreenshot("forge-new-layout");
            context.runOnClient(c -> {
                float scale = Math.min(1.5F, Math.min(c.screen.width / 544F, c.screen.height / 224F));
                click(c, Math.round(c.screen.width / scale) / 2, 7, scale);
            });
            context.waitTicks(15);
            context.takeScreenshot("forge-inventory-open");
            context.runOnClient(c -> {
                float scale = Math.min(1.5F, Math.min(c.screen.width / 544F, c.screen.height / 224F));
                int x = Math.round(c.screen.width / scale) / 2 - 85;
                int y = 22;
                click(c, x + 8, y + 82, scale);
                click(c, 100, 52, scale);
            });
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var forge = (net.krodark.asterion.block.CrucibleBlockEntity)server.overworld().getBlockEntity(pos);
                if (forge.materialUnits() != 1 || forge.heatControl() <= 0)
                    throw new AssertionError("Forge screen inventory or heat click did not reach the server");
            });
            context.runOnClient(c -> c.setScreen(null));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.getLevel(Asterion.ASTERION_LEVEL), 96, 180, 96, Set.of(), 40, 30, true);
            });
            context.waitTicks(100);
            context.runOnClient(c -> {
                int verified = Integer.getInteger("asterion.verifiedTextureFrames", 0);
                if (verified < 8) throw new AssertionError("Texture interpolation cache was not exercised");
                Asterion.LOGGER.info("PASS: {} interpolated texture frames match original pixels exactly", verified);
            });
            System.clearProperty("asterion.verifyTextureFrames");
            try (var recording = new jdk.jfr.Recording()) {
                recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(10));
                recording.enable("jdk.ObjectAllocationSample");
                recording.start();
                context.waitTicks(200);
                recording.stop();
                recording.dump(java.nio.file.Path.of("dimension-profile.jfr"));
                context.runOnClient(c -> Asterion.LOGGER.info("Dimension profile: {} FPS, {}x{}", c.getFps(), c.getWindow().getWidth(), c.getWindow().getHeight()));
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            context.takeScreenshot("dimension-performance-scene");
        } finally {
            System.clearProperty("asterion.verifyTextureFrames");
            System.clearProperty("asterion.verifiedTextureFrames");
        }
    }
    private static void click(net.minecraft.client.Minecraft c, double x, double y, float scale) {
        var event = new net.minecraft.client.input.MouseButtonEvent(x * scale, y * scale,
                new net.minecraft.client.input.MouseButtonInfo(0, 0));
        c.screen.mouseClicked(event, false);
        c.screen.mouseReleased(event);
    }
}

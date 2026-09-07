package net.krodark.asterion.dev.verification;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.hud.MazeObjectiveOverlay;
import net.krodark.asterion.game.SharedObjectiveProgress;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ObjectiveSkippingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("execute in asterion:asterion_dimension run tp @a 200 200 200");
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = level.players().getFirst();
                player.getInventory().clearContent();
                player.setPos(200, 200, 200);
                var teammate = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "ObjectiveTeammate"), ClientInformation.createDefault());
                teammate.connection = player.connection;
                teammate.setPos(800, 200, 800);
                level.addNewPlayer(teammate);
                try {
                    var progress = SharedObjectiveProgress.get(level);
                    progress.observe(level);
                    check(progress.stage() == 0, "Wrong-floor arena progress");
                    teammate.setPos(800, net.krodark.asterion.worldgen.LabyrinthLevels.FORGE_ROOF_Y - 2, 800);
                    progress.observe(level);
                    teammate.setPos(800, 200, 800);
                    player.getInventory().setItem(0, new ItemStack(Asterion.MINOTAUR_KEY_CAST));
                    teammate.getInventory().setItem(0, new ItemStack(Asterion.BONESTEEL_INGOT));
                    progress.observe(level);
                    check(progress.stage() == 6, "Split inventory milestones did not skip together");
                    teammate.getInventory().setItem(0, new ItemStack(Asterion.MINOTAUR_KEY));
                    progress.observe(level);
                    check(progress.stage() == 7, "Teammate key did not advance shared progress");
                    teammate.getInventory().clearContent();
                    player.getInventory().clearContent();
                    progress.observe(level);
                    check(progress.stage() == 7, "Consumed item regressed progress");
                    var encoded = SharedObjectiveProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow();
                    var restored = SharedObjectiveProgress.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
                    check(restored.stage() == 7, "Saved progress was lost");
                } finally {
                    level.removePlayerImmediately(teammate, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                expect("REACH_ARENA_DOORS");
                client.player.getInventory().setItem(0, new ItemStack(Asterion.OMEGA_KEY));
                for (int i = 0; i < 20; i++) MazeObjectiveOverlay.tick(client);
                expect("REACH_ARENA_DOORS");
                MazeObjectiveOverlay.armAfterBossWipe();
                MazeObjectiveOverlay.tick(client);
                expect("REACH_ARENA_DOORS");
            });
            world.getServer().runCommand("execute in minecraft:overworld run tp @a 200 200 200");
            context.waitTicks(10);
            world.getServer().runCommand("execute in asterion:asterion_dimension run tp @a 200 200 200");
            context.waitTicks(20);
            context.runOnClient(client -> expect("REACH_ARENA_DOORS"));
            Asterion.LOGGER.info("PASS: shared objective skipping, split inventories, saved progress, server-to-client sync and re-entry");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expect(String expected) {
        try {
            var field = MazeObjectiveOverlay.class.getDeclaredField("stage");
            field.setAccessible(true);
            check(field.get(null).toString().equals(expected), "Expected " + expected + ", got " + field.get(null));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}

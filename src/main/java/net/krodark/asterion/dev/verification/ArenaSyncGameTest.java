package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Place an arena slice AFTER the client has loaded its empty chunk. */
public final class ArenaSyncGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a -8 72 -8");
            context.waitTicks(60);
            var expected = new java.util.HashMap<BlockPos, BlockState>();
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                AuthoredCatacombs.placeArenaChunk(level, level.getChunk(-1, -1));
                int solid = 0;
                for (var pos : BlockPos.betweenClosed(-16, AuthoredCatacombs.ARENA_BASE_Y, -16,
                        -1, AuthoredCatacombs.ARENA_BASE_Y + 47, -1)) {
                    var state = level.getBlockState(pos);
                    if (!state.isAir()) solid++;
                    expected.put(pos.immutable(), state);
                }
                if (solid < 100) throw new AssertionError("Arena slice is missing its structure");
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                for (var entry : expected.entrySet())
                    if (!client.level.getBlockState(entry.getKey()).equals(entry.getValue()))
                        throw new AssertionError("Arena block not synchronized at " + entry.getKey()
                                + ": expected " + entry.getValue() + ", got " + client.level.getBlockState(entry.getKey()));
            });
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                // Leave the saved completion marker intact while simulating a partial chunk.
                for (var pos : BlockPos.betweenClosed(-16, AuthoredCatacombs.ARENA_BASE_Y, -16,
                        -1, AuthoredCatacombs.ARENA_BASE_Y, -1))
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                AuthoredCatacombs.placeArenaChunk(level, level.getChunk(-1, -1));
                for (var entry : expected.entrySet())
                    if (!level.getBlockState(entry.getKey()).equals(entry.getValue()))
                        throw new AssertionError("Marked partial arena did not repair at " + entry.getKey());
            });
            context.waitTicks(10);
            context.takeScreenshot("arena-synchronized-structure");
            net.krodark.asterion.Asterion.LOGGER.info("PASS: all 12,288 arena slice blocks synchronized after initial chunk delivery");
        }
    }
}

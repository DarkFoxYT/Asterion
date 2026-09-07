package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.krodark.asterion.worldgen.CatacombArena;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;

public final class BackgroundBlockScanGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                int floor = AuthoredCatacombs.ARENA_FLOOR_Y;
                var positions = new BlockPos[]{new BlockPos(-17, floor, -16), new BlockPos(16, floor + 6, 17),
                        new BlockPos(0, floor + 2, 0), new BlockPos(0, floor + 5, 0)};
                for (var pos : positions) {
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    level.setBlock(pos, Asterion.GREEK_BRAZIER.defaultBlockState(), 2);
                }
                verify(level);
                level.setBlock(positions[0], Blocks.AIR.defaultBlockState(), 2);
                verify(level);
                level.setBlock(positions[0], Asterion.GREEK_BRAZIER.defaultBlockState(), 2);
                verify(level);
                Asterion.LOGGER.info("PASS: palette scan matches exhaustive scan after insertion/removal, across chunk edges and stacked roots");
            });
        }
    }
    private static void verify(net.minecraft.server.level.ServerLevel level) {
        var expected = new ArrayList<BlockPos>();
        int radius = AuthoredCatacombs.ARENA_RADIUS, floor = AuthoredCatacombs.ARENA_FLOOR_Y;
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            for (int y = floor; y <= floor + 6; y++) {
                cursor.set(x, y, z);
                var state = level.getBlockState(cursor);
                if (state.is(Asterion.GREEK_BRAZIER) && net.krodark.asterion.block.GreekBrazierBlock.isRoot(state)) {
                    expected.add(cursor.immutable()); break;
                }
            }
        }
        CatacombArena.invalidateBrazierScan(level);
        if (!CatacombArena.braziers(level).equals(expected)) throw new AssertionError("Background scan changed results/order");
    }
}

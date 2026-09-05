package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ShaleFormationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.Set;

public final class ShaleFormationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, .5, 207, -17, Set.of(), 0, 0, true);
                player.setNoGravity(true);
                for (var pos : BlockPos.betweenClosed(-6, 199, -10, 6, 199, 4))
                    level.setBlock(pos, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                for (int material = 0; material < 2; material++) {
                    var block = material == 0 ? Asterion.SHALE_FORMATION : Asterion.SHADED_SHALE_FORMATION;
                    for (int hanging = 0; hanging < 2; hanging++) {
                        int x = -4 + material * 5 + hanging * 2;
                        level.setBlock(new BlockPos(x, 218, 0), Asterion.SHALE.defaultBlockState(), 18);
                        for (int size = 1; size <= 4; size++) {
                            var state = block.defaultBlockState().setValue(ShaleFormationBlock.THICKNESS, size)
                                    .setValue(ShaleFormationBlock.HANGING, hanging == 1);
                            var pos = new BlockPos(x, hanging == 1 ? 209 + size * 2 : 208 - size * 2, 0);
                            level.setBlock(pos, state, 18);
                            level.setBlock(pos.above(), state, 18);
                            var collision = state.getCollisionShape(level, pos);
                            if (Shapes.joinIsNotEmpty(collision, state.getShape(level, pos), BooleanOp.NOT_SAME))
                                throw new AssertionError("Selection and collision disagree");
                            if (collision.toAabbs().size() != 1)
                                throw new AssertionError("Formation must have one box per block");
                            var bounds = collision.bounds();
                            if (bounds.minY != 0 || bounds.maxY != 1)
                                throw new AssertionError("Formation must extend the full block height");

                        }
                    }
                }
            });
            context.waitTicks(50);
            context.runOnClient(c -> { c.player.setNoGravity(true); c.options.hideGui = true; });
            context.takeScreenshot("shale-stalactites-and-stalagmites");
            Asterion.LOGGER.info("PASS: both shale materials, all four widths and both orientations have one full-height collision box");
        }
    }
}

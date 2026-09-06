package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.PedestalContent;
import net.krodark.asterion.block.PedestalBlock;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import java.util.Set;

public final class PedestalGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        BlockPos pos = new BlockPos(0, 200, 0);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                for (var floor : BlockPos.betweenClosed(-4,199,-4,4,199,4)) level.setBlock(floor, Asterion.SHALE_BRICKS.defaultBlockState(),18);
                level.setBlock(pos, PedestalContent.BLOCK.defaultBlockState(), 3);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, .5, 200, -3, Set.of(), 0, 7, true);
                player.getInventory().clearContent();
            });
            context.waitTicks(30);
            context.takeScreenshot("afterblow-pedestal");
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
                var state = level.getBlockState(pos);
                state.useWithoutItem(level, player, hit);
                state = level.getBlockState(pos);
                if (!state.getValue(PedestalBlock.CLAIMED) || player.getInventory().countItem(Asterion.AFTERBLOW) != 1)
                    throw new AssertionError("Pedestal failed to grant exactly one Afterblow");
                state.useWithoutItem(level, player, hit);
                if (player.getInventory().countItem(Asterion.AFTERBLOW) != 1) throw new AssertionError("Pedestal duplicated Afterblow");
                var drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, level.getBlockEntity(pos));
                var item = drops.stream().filter(stack -> stack.is(PedestalContent.ITEM)).findFirst().orElseThrow();
                var properties = item.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
                if (properties == null || !properties.apply(PedestalContent.BLOCK.defaultBlockState()).getValue(PedestalBlock.CLAIMED))
                    throw new AssertionError("Broken pedestal lost its claimed state");
                Asterion.LOGGER.info("PASS: pedestal grants Afterblow once and keeps its empty state in block drops");
            });
            context.waitTicks(15);
            context.takeScreenshot("afterblow-pedestal-empty");
        }
    }
}

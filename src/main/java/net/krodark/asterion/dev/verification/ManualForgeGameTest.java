package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class ManualForgeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(AncientBoneCheck::run);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                var pos = new net.minecraft.core.BlockPos(0, 200, 0);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(pos, net.krodark.asterion.Asterion.CRUCIBLE.defaultBlockState(), 18);
                net.krodark.asterion.Asterion.CRUCIBLE.setPlacedBy(level, pos, level.getBlockState(pos), player,
                        new net.minecraft.world.item.ItemStack(net.krodark.asterion.Asterion.CRUCIBLE));
                var forge = (net.krodark.asterion.block.CrucibleBlockEntity)level.getBlockEntity(pos);
                forge.insert(player, new net.minecraft.world.item.ItemStack(net.krodark.asterion.Asterion.INGOT_CAST));
                for (var item : java.util.List.of(net.krodark.asterion.game.AncientContent.ANCIENT_BONE,
                        net.krodark.asterion.game.AncientContent.ANCIENT_BONE, net.krodark.asterion.game.AncientContent.ANCIENT_BONE,
                        net.krodark.asterion.Asterion.CELESTIAL_STEEL_INGOT, net.minecraft.world.item.Items.IRON_INGOT))
                    forge.insert(player, new net.minecraft.world.item.ItemStack(item));
                player.teleportTo(level, .5, 200, -4, java.util.Set.of(), 0, 0, true);
                player.setNoGravity(true);
                forge.open(player);
            });
            context.waitTicks(50);
            context.runOnClient(client -> {
                if (!(client.screen instanceof net.krodark.asterion.client.CrucibleScreen))
                    throw new AssertionError("Five-ingredient Forge did not open");
            });
            world.getServer().runOnServer(server -> {
                var forge = server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 200, 0));
                if (!(forge instanceof net.krodark.asterion.block.CrucibleBlockEntity contents) || contents.materialUnits() != 5)
                    throw new AssertionError("Five-ingredient Forge disappeared before rendering");
            });
            context.takeScreenshot("forge-five-ingredients");
        }
    }
}

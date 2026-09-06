package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

public final class ForgeInteractionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        context.runOnClient(c -> com.meekdev.amnetic.client.bloom.Bloom.settings().enabled(true).all(false).occlude(true).threshold(0));
        try (var world = context.worldBuilder().create()) {
            BlockPos pos = new BlockPos(0, 200, 0);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var recipe = new net.krodark.asterion.recipe.ForgedSwordRecipe();
                var parts = new java.util.ArrayList<>(java.util.List.of(new ItemStack(Asterion.FORGED_SWORD_BLADE),
                        new ItemStack(Asterion.FORGED_SWORD_GUARD), new ItemStack(Asterion.FORGED_SWORD_POMMEL),
                        new ItemStack(Asterion.DEADWOOD_STICK)));
                if (recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(2, 2, parts)).isEmpty())
                    throw new AssertionError("Deadwood handle did not assemble sword");
                parts.set(3, new ItemStack(net.minecraft.world.item.Items.STICK));
                if (!recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(2, 2, parts)).isEmpty())
                    throw new AssertionError("Vanilla stick accepted as forged handle");
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, .5, 200, -4, Set.of(), 0, 0, true);
                player.setNoGravity(true);
                for (var floor : BlockPos.betweenClosed(-10, 199, -10, 10, 199, 10))
                    level.setBlock(floor, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
                forge.insert(player, new ItemStack(Asterion.INGOT_CAST));
                forge.insert(player, new ItemStack(Asterion.TARNISHED_GOLD_INGOT));
                forge.insert(player, new ItemStack(Asterion.CELESTIAL_STEEL_INGOT));
                try {
                    var heat = CrucibleBlockEntity.class.getDeclaredField("temperature"); heat.setAccessible(true); heat.setInt(forge, 900);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                player.getInventory().setItem(0, new ItemStack(Asterion.CELESTIAL_GOLD_INGOT, 4));
            });
            context.waitTicks(25);
            world.getServer().runOnServer(server -> ((CrucibleBlockEntity)server.overworld().getBlockEntity(pos))
                    .open(server.getPlayerList().getPlayers().getFirst()));
            context.waitTicks(35);
            context.runOnClient(c -> {
                if (!(c.screen instanceof CrucibleScreen)) throw new AssertionError("Forge did not open");
                c.player.setNoGravity(true);
                 
                c.options.hideGui = false;
            });
            context.takeScreenshot("forge-gradient-and-contents");
            context.runOnClient(c -> {
                if (net.krodark.asterion.client.light.AmneticBoneEmission.submissions() == 0)
                    throw new AssertionError("Forge contents did not reach Amnetic emission capture");
            });
            context.runOnClient(c -> click(c, right(c) + 10, 74));
            context.waitTicks(15);
            context.runOnClient(c -> {
                int x = Math.round(c.screen.width / scale(c)) / 2 - 85;
                int y = Math.max(8, Math.round(c.screen.height / scale(c)) - 162);
                click(c, x + 8, y + 82);
            });
            context.waitTicks(11);
            context.runOnClient(c -> {
                if (ForgeItemFlights.activeCount() != 1) throw new AssertionError("Accepted item did not produce one 3D flight");
            });
            context.takeScreenshot("forge-item-in-flight");
            world.getServer().runOnServer(server -> {
                var forge = (CrucibleBlockEntity)server.overworld().getBlockEntity(pos);
                var player = server.getPlayerList().getPlayers().getFirst();
                if (forge.materialUnits() != 3 || player.getInventory().getItem(0).getCount() != 3)
                    throw new AssertionError("Forge insertion duplicated or lost an ingredient");
            });
            context.waitTicks(16);
            context.takeScreenshot("forge-item-melting");
            context.waitTicks(9);
            context.runOnClient(c -> click(c, right(c) + 19, 86));
            context.waitTicks(3);
            world.getServer().runOnServer(server -> {
                var forge = (CrucibleBlockEntity)server.overworld().getBlockEntity(pos);
                var player = server.getPlayerList().getPlayers().getFirst();
                if (forge.materialUnits() != 2 || player.getInventory().getItem(0).getCount() != 4)
                    throw new AssertionError("Clicking an ingredient did not return it");
            });
            context.runOnClient(c -> { click(c, 140, 107); click(c, right(c) - 9, 107);
                click(c, Math.round(c.screen.width / scale(c)) / 2, Math.round(c.screen.height / scale(c)) - 75); });
            context.waitTicks(18);
            context.takeScreenshot("forge-panels-retracted");
            context.runOnClient(c -> { click(c, 12, 107); });
            context.waitTicks(18);
            context.runOnClient(c -> click(c, 100, 52));
            context.waitTicks(4);
            world.getServer().runOnServer(server -> {
                if (((CrucibleBlockEntity)server.overworld().getBlockEntity(pos)).heatControl() <= 0)
                    throw new AssertionError("Reopened heat panel lost its click target");
            });
            context.runOnClient(c -> c.screen.onClose());
            context.waitTicks(50);
            context.runOnClient(c -> {
                if (c.screen != null || CrucibleCamera.active() || c.options.hideGui)
                    throw new AssertionError("Forge close did not restore normal view and HUD");
            });
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                    .setGameMode(net.minecraft.world.level.GameType.CREATIVE));
            context.waitTicks(5);
            context.runOnClient(c -> c.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player)));
            context.waitTicks(5);
            context.runOnClient(c -> {
                if (!(c.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen))
                    throw new AssertionError("Creative inventory failed to open");
                long swords = Asterion.FORGING_ITEM_GROUP.getDisplayItems().stream()
                        .filter(stack -> stack.is(Asterion.FORGED_SWORD) && stack.getCount() == 1).count();
                if (swords != 9) throw new AssertionError("Missing creative forged sword variants: " + swords);
            });
            context.takeScreenshot("creative-inventory-after-forging");
            context.runOnClient(c -> c.setScreen(null));
            Asterion.LOGGER.info("PASS: Forge gradient, server-accepted 3D insertion, contents, retractable controls and restored HUD");
        }
    }
    private static float scale(net.minecraft.client.Minecraft c) { return Math.min(1.5F, Math.min(c.screen.width / 544F, c.screen.height / 224F)); }
    private static int right(net.minecraft.client.Minecraft c) { return Math.round(c.screen.width / scale(c)) - 132; }
    private static void click(net.minecraft.client.Minecraft c, double x, double y) {
        var event = new net.minecraft.client.input.MouseButtonEvent(x * scale(c), y * scale(c),
                new net.minecraft.client.input.MouseButtonInfo(0, 0));
        c.screen.mouseClicked(event, false); c.screen.mouseReleased(event);
    }
}

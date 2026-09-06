package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.client.forge.CrucibleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class ForgeRecipesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            BlockPos pos = new BlockPos(0, 200, 0);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                Asterion.CRUCIBLE.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(Asterion.CRUCIBLE));
                var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
                forge.insert(player, new ItemStack(Asterion.INGOT_CAST));
                player.getInventory().setItem(0, new ItemStack(Asterion.MINOTAUR_KEY_CAST));
                player.teleportTo(level, .5, 200, -4, java.util.Set.of(), 0, 0, true);
                player.setNoGravity(true);
                forge.open(player);
            });
            context.waitTicks(40);
            context.runOnClient(c -> {
                click(c, center(c) + 45, 7);
                checkCount(c, 6);
                for (int i = 0; i < 4; i++) click(c, center(c) + 100, 34);
                var panel = field(c.screen, "recipes");
                var recipes = (java.util.List<?>)field(panel, "available");
                var recipe = (net.krodark.asterion.compat.CrucibleViewerRecipe)recipes.get((int)field(panel, "page"));
                if (!recipe.id().getPath().endsWith("celestial_gold")) throw new AssertionError("Recipe paging did not reach gold");
            });
            context.waitTicks(3);
            context.takeScreenshot("forge-recipes-gold");
            context.runOnClient(c -> click(c, center(c) + 100, 34));
            context.waitTicks(3);
            context.takeScreenshot("forge-recipes-bonesteel");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ((CrucibleBlockEntity)server.overworld().getBlockEntity(pos)).removeMold(player);
                player.getInventory().clearContent();
            });
            context.waitTicks(10);
            context.runOnClient(c -> checkCount(c, 0));
            context.takeScreenshot("forge-recipes-no-molds");
            context.runOnClient(c -> {
                click(c, center(c), 7);
                if ((boolean)field(c.screen, "recipesOpen") || !(boolean)field(c.screen, "inventoryOpen"))
                    throw new AssertionError("Inventory and recipes overlap");
                Asterion.LOGGER.info("PASS: Forge recipe ownership, installed molds, paging, live removal and inventory toggle");
            });
        }
    }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void checkCount(net.minecraft.client.Minecraft c, int count) {
        var available = (java.util.List<?>)field(field(c.screen, "recipes"), "available");
        if (available.size() != count) throw new AssertionError("Expected " + count + " recipes, got " + available.size());
    }
    private static float scale(net.minecraft.client.Minecraft c) {
        return Math.min(1.5F, Math.min(c.screen.width / 544F, c.screen.height / 224F));
    }
    private static int center(net.minecraft.client.Minecraft c) { return Math.round(c.screen.width / scale(c)) / 2; }
    private static void click(net.minecraft.client.Minecraft c, double x, double y) {
        var event = new net.minecraft.client.input.MouseButtonEvent(x * scale(c), (y + 4) * scale(c),
                new net.minecraft.client.input.MouseButtonInfo(0, 0));
        c.screen.mouseClicked(event, false); c.screen.mouseReleased(event);
    }
}

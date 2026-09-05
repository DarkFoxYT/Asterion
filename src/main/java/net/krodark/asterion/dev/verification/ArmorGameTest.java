package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.ArmorContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

public final class ArmorGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 0, 200.5, -7, Set.of(), 0, 5, true);
                player.setNoGravity(true);
                for (var pos : BlockPos.betweenClosed(-8, 199, -9, 8, 199, 5))
                    level.setBlock(pos, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                for (int i = 0; i < ArmorContent.SETS.size(); i++) {
                    var set = ArmorContent.SETS.get(i);
                    var stand = new ArmorStand(level, -3 + i * 2, 200, 0);
                    stand.setYRot(180); stand.yBodyRot = 180; stand.yHeadRot = 180;
                    for (int j = 0; j < 4; j++) {
                        var stack = new ItemStack(set.pieces().get(j));
                        var equipment = stack.get(DataComponents.EQUIPPABLE);
                        if (equipment == null || equipment.slot() != slots[j] || stack.getMaxDamage() <= 0
                                || stack.get(DataComponents.REPAIRABLE) == null)
                            throw new AssertionError("Missing armor properties: " + set.name());
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                        if (server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Asterion.id(id))).isEmpty())
                            throw new AssertionError("Armor recipe did not load: " + id);
                        stand.setItemSlot(slots[j], stack);
                        player.getInventory().setItem(i * 4 + j, stack.copy());
                    }
                    level.addFreshEntity(stand);
                }
            });
            context.waitTicks(50);
            context.runOnClient(c -> { c.player.setNoGravity(true); c.options.hideGui = true; });
            context.takeScreenshot("four-custom-armor-sets");
            context.runOnClient(c -> {
                c.options.hideGui = false;
                c.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player));
            });
            context.waitTicks(4);
            context.takeScreenshot("custom-armor-inventory");
            context.runOnClient(c -> c.setScreen(null));
            Asterion.LOGGER.info("PASS: sixteen armor pieces have equipment slots, durability, repairs and loaded recipes");
        }
    }
}

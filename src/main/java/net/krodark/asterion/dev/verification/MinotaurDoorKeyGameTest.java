package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurDoorBlock;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class MinotaurDoorKeyGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos root = new BlockPos(40, 120, 40);
                MinotaurDoorBlock.place(level, root, Direction.SOUTH);
                var door = (MinotaurDoorBlockEntity) level.getBlockEntity(root);
                player.setGameMode(GameType.SURVIVAL);
                door.interact(player, ItemStack.EMPTY);
                check(door.angle(MinotaurDoorMotion.OPEN_TICKS) == 0, "Locked door opened empty-handed");
                var key = new ItemStack(Asterion.MINOTAUR_KEY);
                door.interact(player, key);
                check(key.isEmpty(), "First unlock did not consume its key");
                check(door.angle(MinotaurDoorMotion.OPEN_TICKS) == MinotaurDoorMotion.OPEN_ANGLE, "Key did not open door");
                door.closeForEncounter();
                door.interact(player, ItemStack.EMPTY);
                door.interact(player, new ItemStack(Items.STICK));
                check(door.angle(MinotaurDoorMotion.OPEN_TICKS) == 0, "Previously unlocked door opened without key");
                door.openAfterVictory();
                check(door.angle(MinotaurDoorMotion.OPEN_TICKS) == MinotaurDoorMotion.OPEN_ANGLE, "Victory opening was blocked");
                door.closeForEncounter();
                player.setGameMode(GameType.CREATIVE);
                door.interact(player, ItemStack.EMPTY);
                check(door.angle(MinotaurDoorMotion.OPEN_TICKS) == MinotaurDoorMotion.OPEN_ANGLE, "Creative toggle was blocked");
            });
            Asterion.LOGGER.info("PASS: survival Minotaur doors require keys even after unlock; victory and creative still work");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

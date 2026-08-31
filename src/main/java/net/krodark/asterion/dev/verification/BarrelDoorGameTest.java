package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Opt-in real client/server verification; never touches an existing save. */
public final class BarrelDoorGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        BlockPos root = new BlockPos(0, 121, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("tp @a 0.5 121 -8 0 -5");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
                    level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 3);
                player.setGameMode(GameType.SURVIVAL);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    player.setYRot(facing.getOpposite().toYRot());
                    ItemStack stack = new ItemStack(Asterion.BARREL_DOOR);
                    var hit = new BlockHitResult(Vec3.atBottomCenterOf(root), Direction.UP, root.below(), false);
                    ((BlockItem)stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
                    check(level.getBlockEntity(root) instanceof BarrelDoorBlockEntity, "Item placement failed: " + facing);
                    check(stack.isEmpty(), "Survival placement did not consume exactly one door");
                    for (int c = 0; c < 3; c++) for (int r = 0; r < 4; r++) {
                        var pos = BarrelDoorBlock.part(root, facing, c, r);
                        var state = level.getBlockState(pos);
                        check(state.is(Asterion.BARREL_DOOR), "Missing 3x4 part");
                        check(BarrelDoorBlock.root(pos, state).equals(root), "Wrong anchor");
                        check(!state.getCollisionShape(level, pos).isEmpty(), "Closed collision hole");
                    }
                    BarrelDoorBlock.removeDoor(level, root, facing);
                }
                BarrelDoorBlock.place(level, root, Direction.NORTH);
            });
            context.runOnClient(client -> client.options.hideGui = true);
            context.waitTicks(20);
            context.takeScreenshot("barrel-door-closed");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var door = (BarrelDoorBlockEntity)level.getBlockEntity(root);
                door.interact(mc.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY);
                check(door.getBlockState().getValue(BarrelDoorBlock.OPEN), "Door requires a key");
            });
            context.waitTicks(24);
            context.takeScreenshot("barrel-door-open");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var door = (BarrelDoorBlockEntity)level.getBlockEntity(root);
                check(Math.abs(door.angle(0) - Math.PI / 2) < .001, "Swing did not settle at 90 degrees");
                check(door.getBlockState().getCollisionShape(level, root).isEmpty(), "Open passage is blocked");
                var leaf = root.east().south(2).above(2);
                var leafState = level.getBlockState(leaf);
                check(leafState.is(Asterion.BARREL_DOOR) && leafState.getValue(BarrelDoorBlock.WING), "Missing swung leaf");
                check(!leafState.getCollisionShape(level, leaf).isEmpty(), "Swung leaf lacks collision");
                check(BarrelDoorBlock.root(leaf, leafState).equals(root), "Open leaf cannot resolve its controller");
                door.interact(mc.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY);
            });
            context.waitTicks(24);
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var door = (BarrelDoorBlockEntity)level.getBlockEntity(root);
                check(!door.getBlockState().getValue(BarrelDoorBlock.OPEN), "Closing did not latch");
                check(Math.abs(door.angle(0)) < .001, "Closed pose did not settle");
                for (int depth = 1; depth <= 3; depth++) for (int row = 0; row < 4; row++)
                    check(level.getBlockState(root.east().south(depth).above(row)).isAir(), "Closing left an invisible leaf");
                level.setBlock(root.east().south(2), Blocks.STONE.defaultBlockState(), 3);
                door.interact(mc.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY);
                check(!door.getBlockState().getValue(BarrelDoorBlock.OPEN), "Door opened through a wall");
                Asterion.BARREL_DOOR.playerWillDestroy(level, root.above(3), level.getBlockState(root.above(3)),
                        mc.getPlayerList().getPlayers().getFirst());
                for (int c = 0; c < 3; c++) for (int r = 0; r < 4; r++)
                    check(level.getBlockState(BarrelDoorBlock.part(root, Direction.NORTH, c, r)).isAir(), "Orphan door part");
            });
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

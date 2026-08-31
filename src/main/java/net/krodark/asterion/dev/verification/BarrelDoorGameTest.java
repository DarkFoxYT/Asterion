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
                GreekBrazierBlock.placeStructure((pos, state) -> level.setBlock(pos, state, 3), new BlockPos(10, 126, 0));
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
            context.runOnClient(client -> {
                int previous = net.krodark.asterion.AsterionConfig.INSTANCE.brightnessPercent;
                double vanilla = client.options.gamma().get();
                try {
                    var extractor = new net.minecraft.client.renderer.LightmapRenderStateExtractor(client.gameRenderer, client);
                    var light = new net.minecraft.client.renderer.state.LightmapRenderState();
                    for (int brightness : new int[]{0, 100, -1}) {
                        net.krodark.asterion.AsterionConfig.INSTANCE.brightnessPercent = brightness;
                        extractor.tick(); extractor.extract(light, 0);
                        check(Math.abs(light.brightness - (brightness < 0 ? vanilla : brightness / 100F)) < .001,
                                "Rendered brightness override failed");
                        check(client.options.gamma().get() == vanilla, "Brightness override rewrote vanilla preference");
                    }
                } finally { net.krodark.asterion.AsterionConfig.INSTANCE.brightnessPercent = previous; }
            });
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (int x = 9; x <= 11; x++) for (int z = -1; z <= 1; z++)
                    check(level.getBlockState(new BlockPos(x, 126, z)).is(Asterion.GREEK_BRAZIER), "Unsupported brazier broke");
                Asterion.LOGGER.info("PASS: unsupported brazier survives; Moody/Bright/Vanilla render settings preserve vanilla preference");
            });
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
                for (int amount = 0; amount <= 9; amount++) {
                    var fluid = amount == 0 ? net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()
                            : net.krodark.asterion.fluid.HeavyWaterlogging.fluid(amount);
                    for (int c = 0; c < 3; c++) for (int r = 0; r < 4; r++)
                        level.setBlock(BarrelDoorBlock.part(root, Direction.NORTH, c, r), fluid.createLegacyBlock(), 2);
                    for (int d = 1; d <= 3; d++) for (int r = 0; r < 4; r++)
                        level.setBlock(root.east().south(d).above(r), fluid.createLegacyBlock(), 2);
                    BarrelDoorBlock.place(level, root, Direction.NORTH);
                    check(level.getFluidState(root).getType() == fluid.getType(), "Placing door erased water");
                    check(BarrelDoorBlock.prepareSwing(level, root, Direction.NORTH), "Door cannot swing through water");
                    BarrelDoorBlock.setOpen(level, root, Direction.NORTH, true);
                    BarrelDoorBlock.setOpen(level, root, Direction.NORTH, false);
                    check(level.getFluidState(root.east().south(2)).getType() == fluid.getType(), "Closing erased wing water");
                    BarrelDoorBlock.removeDoor(level, root, Direction.NORTH);
                    check(level.getFluidState(root).getType() == fluid.getType()
                            && level.getFluidState(root).getAmount() == fluid.getAmount(), "Removing door lost fluid or height");
                }
                Asterion.LOGGER.info("PASS: barrel door preserves vanilla water and all Heavy Water layers through placement, swing and removal");
                level.setBlock(new BlockPos(0, 121, 0), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(0, 122, 0), Asterion.LABYRINTH_VINE.defaultBlockState()
                        .setValue(LabyrinthVineBlock.FACING, Direction.UP), 3);
                level.setBlock(new BlockPos(3, 123, 0), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(3, 122, 0), Asterion.LABYRINTH_VINE.defaultBlockState()
                        .setValue(LabyrinthVineBlock.FACING, Direction.DOWN), 3);
            });
            server.runCommand("time set midnight");
            server.runCommand("tp @a 1.5 121 -4 0 -5");
            context.waitTicks(30);
            context.takeScreenshot("vine-glow-upright-and-hanging");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

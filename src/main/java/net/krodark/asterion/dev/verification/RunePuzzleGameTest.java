package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class RunePuzzleGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        BlockPos root = new BlockPos(0, 121, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("tp @a 0.5 121 -8 0 0");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
                    level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 3);
                for (int index = 0; index < 24; index++) for (Direction facing : Direction.Plane.HORIZONTAL) {
                    RuneBlock block = Asterion.RUNE_BLOCKS[index];
                    player.setYRot(facing.getOpposite().toYRot());
                    ItemStack placed = new ItemStack(block);
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atBottomCenterOf(root),
                            Direction.UP, root.below(), false);
                    ((BlockItem)placed.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, placed, hit));
                    check(placed.isEmpty(), "Item placement failed or did not consume one plaque");
                    var rune = (RuneBlockEntity)level.getBlockEntity(root);
                    check(!rune.isWorldGenerated(), "Player placement became a beetle habitat");
                    rune.interact(player, new ItemStack(Asterion.RUNE_STONE_BLOCKS[(index + 1) % 24]));
                    check(!rune.getBlockState().getValue(RuneBlock.POWERED), "Wrong key activated rune");
                    ItemStack key = new ItemStack(Asterion.RUNE_STONE_BLOCKS[index]);
                    rune.interact(player, key);
                    check(key.getCount() == 1, "Reusable key consumed");
                    for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) {
                        var pos = RuneBlock.part(root, facing, c, r);
                        var state = level.getBlockState(pos);
                        check(state.is(block) && RuneBlock.root(pos, state).equals(root), "Missing collision part");
                        check(!state.getCollisionShape(level, pos).isEmpty(), "Missing collision");
                        check((level.getBlockEntity(pos) != null) == RuneBlock.isRoot(state), "Duplicate render anchor");
                        for (Direction side : Direction.values()) {
                            check(state.getSignal(level, pos, side) == 15, "Missing weak output");
                            check(state.getDirectSignal(level, pos, side) == 15, "Missing strong output");
                        }
                    }
                    player.setShiftKeyDown(true);
                    rune.interact(player, ItemStack.EMPTY);
                    player.setShiftKeyDown(false);
                    for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) {
                        var pos = RuneBlock.part(root, facing, c, r);
                        check(!level.getBlockState(pos).getValue(RuneBlock.POWERED), "Reset left a powered part");
                    }
                    RuneBlock.removeRune(level, root, facing);
                }
                Asterion.RUNE_BLOCKS[0].place(level, root, Direction.NORTH);
                level.setBlock(root.north(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
                level.setBlock(root.south(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
                ((RuneBlockEntity)level.getBlockEntity(root)).interact(player, new ItemStack(Asterion.RUNE_STONE_BLOCKS[0]));
                check(level.getBlockState(root.north()).getValue(BlockStateProperties.LIT), "Front lamp not powered");
                check(level.getBlockState(root.south()).getValue(BlockStateProperties.LIT), "Back lamp not powered");
            });
            server.runCommand("tp @a 0.5 121 -8 0 0");
            context.runOnClient(client -> client.options.hideGui = true);
            context.waitTicks(20);
            context.takeScreenshot("rune-powered");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var pos = root.east().above(2);
                Asterion.RUNE_BLOCKS[0].playerWillDestroy(level, pos, level.getBlockState(pos), mc.getPlayerList().getPlayers().getFirst());
                for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++)
                    check(level.getBlockState(RuneBlock.part(root, Direction.NORTH, c, r)).isAir(), "Orphan rune part");
            });
            context.waitTicks(8);
            server.runOnServer(mc -> {
                var level = mc.overworld();
                check(!level.getBlockState(root.north()).getValue(BlockStateProperties.LIT), "Front lamp stuck on");
                check(!level.getBlockState(root.south()).getValue(BlockStateProperties.LIT), "Back lamp stuck on");
                var block = Asterion.RUNE_BLOCKS[0];
                level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, true, mc);
                block.place(level, root, Direction.NORTH);
                var rune = (RuneBlockEntity)level.getBlockEntity(root);
                var bounds = new net.minecraft.world.phys.AABB(root).inflate(32);
                for (int tick = 0; tick < 2400; tick++) RuneBlockEntity.tick(level, root, rune.getBlockState(), rune);
                check(level.getEntitiesOfClass(net.krodark.asterion.entity.RuneBeetleEntity.class, bounds).isEmpty(),
                        "Unproven rune spawned beetles");
                rune.setWorldGenerated(true);
                var restored = (RuneBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                        root, rune.getBlockState(), rune.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                check(restored != null && restored.isWorldGenerated(), "Generated provenance lost on reload");
                for (int tick = 0; tick < 4800; tick++) RuneBlockEntity.tick(level, root, rune.getBlockState(), rune);
                var beetles = level.getEntitiesOfClass(net.krodark.asterion.entity.RuneBeetleEntity.class, bounds);
                check(!beetles.isEmpty() && beetles.size() <= 2, "Natural rune spawn missing or uncapped: " + beetles.size());
                for (var beetle : beetles) {
                    check(level.noCollision(beetle), "Rune beetle spawned inside a block");
                    beetle.discard();
                }
                block.setPlacedBy(level, root, rune.getBlockState(), mc.getPlayerList().getPlayers().getFirst(), new ItemStack(block));
                check(!((RuneBlockEntity)level.getBlockEntity(root)).isWorldGenerated(), "Replacement retained natural provenance");
                Asterion.LOGGER.info("PASS: rune beetle natural-only spawning, saved provenance, player replacement exclusion and local cap");
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

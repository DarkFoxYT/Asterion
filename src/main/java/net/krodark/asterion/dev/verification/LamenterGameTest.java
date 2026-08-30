package net.krodark.asterion.dev.verification;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.block.LamenterBlock;
import net.krodark.asterion.block.LamenterBlockEntity;
import net.krodark.asterion.event.CatacombFloodState;
import net.krodark.asterion.worldgen.CatacombArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Exact server-tick checks in a disposable world, plus a real rendered texture/tear preview. */
public final class LamenterGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        AtomicInteger elapsed = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        BlockPos[] faces = {new BlockPos(0, 124, 0), new BlockPos(4, 124, 0),
                new BlockPos(8, 124, 0), new BlockPos(12, 124, 0)};
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("tp @a 0.5 122 4.5 180 -14");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set day");
            server.runOnServer(mc -> {
                ServerLevel level = mc.overworld();
                for (int x = -5; x <= 17; x++) for (int z = -5; z <= 7; z++)
                    level.setBlock(new BlockPos(x, 121, z), Blocks.STONE.defaultBlockState(), 3);
                for (int x = -2; x <= 2; x++) for (int y = 122; y <= 126; y++)
                    level.setBlock(new BlockPos(x, y, -1), Asterion.ANCIENT_BRICKS.defaultBlockState(), 3);
                for (int i = 0; i < faces.length; i++) {
                    level.setBlock(faces[i], Asterion.LAMENTER.defaultBlockState()
                            .setValue(LamenterBlock.FACING, directions[i]), 3);
                    for (int down = 0; down <= 2; down++)
                        level.setBlock(faces[i].relative(directions[i]).below(down), Blocks.AIR.defaultBlockState(), 3);
                }
                final long start = level.getGameTime() + 1;
                ServerTickEvents.END_LEVEL_TICK.register(ticking -> {
                    if (ticking != level || elapsed.get() >= 521) return;
                    int tick = (int)(level.getGameTime() - start);
                    if (tick < 0) return;
                    try {
                        for (int i = 0; i < faces.length; i++) {
                            BlockPos pos = faces[i], bowl = pos.relative(directions[i]).below(2);
                            if (tick == 0 || tick == 161) level.setBlock(bowl, Asterion.GREEK_BRAZIER.defaultBlockState(), 3);
                            if (tick == 0) {
                                level.getBlockState(pos).useWithoutItem(level, mc.getPlayerList().getPlayers().getFirst(),
                                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                                                directions[i], pos, false));
                                check(level.getBlockState(pos).getValue(LamenterBlock.ACTIVE), "Empty-hand interaction failed");
                            }
                            if (tick == 260) activate(level, pos, true);
                            if (tick == 159 || tick == 250 || tick == 509) check(lit(level, bowl), "Premature extinction at " + tick);
                            if (tick == 160 || tick == 510) check(!lit(level, bowl), "Did not extinguish at exactly 160 ticks: " + directions[i]);
                            if (tick == 161 || tick == 511) activate(level, pos, false);
                            if (tick == 170) level.setBlock(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                            if (tick == 180) check(level.getBlockState(pos).getValue(LamenterBlock.CRYING), "Redstone did not trigger crying");
                            if (tick == 250) level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
                            if (tick == 252) check(!level.getBlockState(pos).getValue(LamenterBlock.CRYING), "Crying did not stop");
                            if (tick == 339) level.setBlock(pos.relative(directions[i]).below(), Blocks.STONE.defaultBlockState(), 3);
                            if (tick == 350) level.setBlock(pos.relative(directions[i]).below(), Blocks.AIR.defaultBlockState(), 3);
                        }
                        if (tick == 520) {
                            ServerLevel maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                            BlockPos floodFace = new BlockPos(0, 12, 0);
                            maze.setBlock(floodFace, Asterion.LAMENTER.defaultBlockState(), 3);
                            CatacombFloodState.setActive(maze, true);
                            check(CatacombFloodState.isFlooding(maze, floodFace), "Catacomb missed flood");
                            check(!CatacombFloodState.isFlooding(maze, new BlockPos(70, 70, 70)), "Flood leaked onto surface");
                            check(!CatacombFloodState.isFlooding(level, new BlockPos(0, 12, 0)), "Flood leaked into Overworld");
                            var blockEntity = (LamenterBlockEntity)maze.getBlockEntity(floodFace);
                            LamenterBlockEntity.tick(maze, floodFace, maze.getBlockState(floodFace), blockEntity);
                            check(maze.getBlockState(floodFace).getValue(LamenterBlock.CRYING), "Flood did not awaken Lamenter");
                            CatacombFloodState.setActive(maze, false);
                            LamenterBlockEntity.tick(maze, floodFace, maze.getBlockState(floodFace), blockEntity);
                            check(!maze.getBlockState(floodFace).getValue(LamenterBlock.CRYING), "Lamenter did not stop after flood");
                            check(WorldGenerator.activeBossBraziers(maze) == 4, "Arena braziers not powering boss");
                            for (Direction direction : directions) {
                                BlockPos face = CatacombArena.lamenter(direction);
                                check(maze.getBlockState(face).is(Asterion.LAMENTER), "Arena missing Lamenter");
                                check(face.relative(direction.getOpposite()).below(2).equals(CatacombArena.brazier(direction)),
                                        "Arena tears miss brazier");
                                net.krodark.asterion.block.GreekBrazierBlock.extinguish(maze, CatacombArena.brazier(direction));
                            }
                            check(WorldGenerator.activeBossBraziers(maze) == 0, "Extinguished braziers still power boss");
                        }
                        elapsed.set(tick);
                    } catch (Throwable error) { failure.set(error); elapsed.set(521); }
                });
            });
            context.waitFor(client -> elapsed.get() >= 80, 1200);
            context.runOnClient(client -> client.options.hideGui = true);
            context.takeScreenshot("lamenter-crying");
            context.waitFor(client -> elapsed.get() >= 521, 1600);
            if (failure.get() != null) throw new AssertionError("Lamenter integration failed", failure.get());
            context.takeScreenshot("lamenter-extinguished");
            Asterion.LOGGER.info("PASS: Lamenter four orientations, exact 160-tick soak, redstone, interrupted tears, flood scope and arena power");
        }
    }
    private static void activate(ServerLevel level, BlockPos pos, boolean active) {
        level.setBlock(pos, level.getBlockState(pos).setValue(LamenterBlock.ACTIVE, active), 3);
    }
    private static boolean lit(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(BlockStateProperties.LIT);
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}

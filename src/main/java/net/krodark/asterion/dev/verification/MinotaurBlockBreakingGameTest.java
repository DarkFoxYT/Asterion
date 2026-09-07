package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

public final class MinotaurBlockBreakingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                boss.setPos(200.5, 180, 200.5);
                var bounds = boss.getBoundingBox().inflate(1.5);
                BlockPos near = new BlockPos(201, 181, 200);
                BlockPos overhead = new BlockPos(200, (int) bounds.maxY - 1, 200);
                BlockPos natural = new BlockPos(199, 181, 200);
                BlockPos far = new BlockPos(220, 181, 200);
                for (BlockPos placed : java.util.List.of(near, overhead, far)) {
                    level.setBlock(placed, Blocks.OBSIDIAN.defaultBlockState(), 2);
                    WorldGenerator.trackPlayerPlacement(level, placed, level.getBlockState(placed));
                }
                level.setBlock(natural, Blocks.OBSIDIAN.defaultBlockState(), 2);
                try {
                    var aiTick = MinotaurEntity.class.getDeclaredMethod("customServerAiStep", net.minecraft.server.level.ServerLevel.class);
                    aiTick.setAccessible(true);
                    boss.tickCount = 4;
                    aiTick.invoke(boss, level);
                    check(level.getBlockState(near).isAir(), "Minotaur did not clear nearby upper-maze player block");
                    check(level.getBlockState(overhead).isAir(), "Minotaur did not clear player ceiling");
                    check(level.getBlockState(natural).is(Blocks.OBSIDIAN), "Untracked world block was destroyed");
                    check(level.getBlockState(far).is(Blocks.OBSIDIAN), "Distant player block was destroyed");
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            Asterion.LOGGER.info("PASS: Minotaur AI clears nearby player obsidian and ceilings, preserving untracked and distant blocks");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

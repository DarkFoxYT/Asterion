package net.krodark.asterion.worldgen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Opt-in integration coverage in an isolated disposable world. */
public final class BiomeFeatureGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                long seed = MazeChunkGenerator.terrainSeed(maze.getChunkSource().randomState());
                check(WorldGenerator.mazeTerrainSeed() == seed, "Runtime seed was not initialized at world startup");
                BlockPos sample = null;
                int limit = AsterionConfig.INSTANCE.mazeRadiusCells * AsterionConfig.INSTANCE.cellSize - 96;
                for (int x = -limit; x <= limit && sample == null; x += 32)
                    for (int z = -limit; z <= limit; z += 32)
                        if (WorldGenerator.mazeBiomeHasFeature(seed, x, z, "moss_patches")) {
                            sample = new BlockPos(x, 0, z);
                            break;
                        }
                check(sample != null, "No overgrowth biome found");
                int vegetation = 0;
                for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                    var chunk = maze.getChunk((sample.getX() >> 4) + dx, (sample.getZ() >> 4) + dz);
                    var cursor = new BlockPos.MutableBlockPos();
                    for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
                        for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                            int floor = WorldGenerator.mazeFloorHeight(seed, x, z);
                            for (int y = floor - 3; y <= floor + 20; y++) {
                                var state = chunk.getBlockState(cursor.set(x, y, z));
                                if (state.is(Asterion.ANCIENT_MOSS) || state.is(Asterion.ANCIENT_MOSS_CARPET)
                                        || state.is(Asterion.SHORT_GRASS)) vegetation++;
                            }
                        }
                }
                check(vegetation > 20, "Generated overgrowth is empty: " + vegetation);
                WorldgenDataChecks.run(maze, sample);
                for (int i = 0; i < 12; i++) MazeNbtStructures.tick(maze);
                // Earlier vegetation must not prevent later features from finding the terrain.
                BlockPos floor = sample.atY(WorldGenerator.mazeFloorHeight(seed, sample.getX(), sample.getZ()));
                for (int dy = 1; dy <= 4; dy++) maze.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), 2);
                maze.setBlock(floor, Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                for (var cover : java.util.List.of(Asterion.ANCIENT_MOSS_CARPET, Asterion.SHORT_GRASS, Asterion.TAINTED_PETALS)) {
                    maze.setBlock(floor.above(), cover.defaultBlockState(), 2);
                    check(floor.equals(OvergrowthFeatureSupport.findFloor(maze, floor.getX(), floor.getZ())),
                            "Existing ground vegetation hid the floor: " + cover);
                }
                maze.setBlock(floor.above(), Blocks.CHEST.defaultBlockState(), 2);
                check(!OvergrowthFeatureSupport.isOpen(maze, floor.above()), "Feature placement would replace a chest");
                WorldGenerator.clearRuntimeState(server);
                WorldGenerator.initializeMazeTerrain(maze);
                check(OvergrowthFeatureSupport.enabled(maze, sample, "moss_patches"), "Biome feature lost after runtime reset");
                check(WorldGenerator.mazeTerrainSeed() == seed, "Reload changed the terrain seed");
                Asterion.LOGGER.info("Biome feature integration passed: {} vegetation blocks in four chunks", vegetation);
            });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

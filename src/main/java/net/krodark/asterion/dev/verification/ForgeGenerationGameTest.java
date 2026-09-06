package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.StructureTemplateAccessor;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

public final class ForgeGenerationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var template = level.getStructureManager().get(Asterion.id("forge/staircase")).orElseThrow();
                var blocks = ((StructureTemplateAccessor)(Object)template).asterion$getPalettes().getFirst().blocks();
                var original = level.getStructureManager().get(Asterion.id("catacombs/corridor_cross_01")).orElseThrow();
                var stairStates = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
                for (var info : blocks) stairStates.put(info.pos(), info.state());
                for (var info : ((StructureTemplateAccessor)(Object)original).asterion$getPalettes().getFirst().blocks())
                    if (info.pos().getX() >= 5 && !info.state().equals(stairStates.get(info.pos().above(39))))
                        throw new AssertionError("Staircase cuts through junction interior at " + info.pos());
                int checked = 0;
                for (int district : new int[]{-1, 0, 1}) {
                    int center = CatacombLayout.ROOT_CENTER + district * AuthoredForge.DISTRICT_SPACING;
                    var socket = AuthoredForge.westSocket(level, ChunkPos.containing(new BlockPos(center, 29, center)));
                    var bottom = template.getJigsaws(BlockPos.ZERO, Rotation.NONE).stream()
                            .filter(p -> p.info().pos().getY() == 1).findFirst().orElseThrow();
                    BlockPos origin = socket.west().subtract(bottom.info().pos());
                    var bounds = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), origin);
                    var chunks = new java.util.ArrayList<ChunkPos>();
                    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
                        for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                            level.getChunk(x, z); chunks.add(new ChunkPos(x, z));
                        }
                    BlockPos sentinel = new BlockPos(bounds.maxX() + 1, bounds.maxY(), bounds.maxZ());
                    level.setBlock(sentinel, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
                    for (boolean reverse : new boolean[]{false, true}) {
                        if (reverse) java.util.Collections.reverse(chunks);
                        for (var info : blocks) if (info.pos().getY() >= 67)
                            level.setBlock(origin.offset(info.pos()), Blocks.AIR.defaultBlockState(), 18);
                        for (var chunk : chunks) ForgeDepths.carveAccess(level, chunk);
                        for (var info : blocks) if (info.pos().getY() >= 67) {
                            BlockPos pos = origin.offset(info.pos());
                            if (!level.getBlockState(pos).equals(info.state()))
                                throw new AssertionError("Forge staircase cut at " + pos + ": expected " + info.state() + ", got " + level.getBlockState(pos));
                            checked++;
                        }
                        if (!level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK))
                            throw new AssertionError("Staircase placement changed its neighbour");
                    }
                }
                Asterion.LOGGER.info("PASS: {} Forge upper-layer blocks preserved across positive/negative districts and both chunk orders; neighbouring structure untouched", checked);
            });
        }
    }
}

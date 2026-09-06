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
                    if ((info.pos().getX() >= 9 || info.pos().getZ() >= 8) && !info.state().equals(stairStates.get(info.pos().above(39))))
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
                    // This test uses an overworld: place the destination room as worldgen would.
                    for (var chunk : chunks) AuthoredForge.place(level, chunk);
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
                        if (!ShaleCavesCheck.route(level, origin.offset(9, 44, 9), socket, center))
                            throw new AssertionError("Wall staircase has no walkable route to Forge at " + center);
                        if (!level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK))
                            throw new AssertionError("Staircase placement changed its neighbour");
                    }
                }
                for (int cx = -1; cx <= 0; cx++) for (int cz = 3; cz <= 5; cz++)
                    AuthoredCatacombs.placeArenaChunk(level, level.getChunk(cx, cz));
                for (int x = -2; x <= 2; x++) for (int z = 62; z <= CatacombLayout.ROOT_CENTER; z++) {
                    BlockPos feet = new BlockPos(x, AuthoredCatacombs.CONNECTOR_Y, z);
                    if (!level.noCollision(new net.minecraft.world.phys.AABB(feet.getX() + .2, feet.getY(), feet.getZ() + .2,
                            feet.getX() + .8, feet.getY() + 1.8, feet.getZ() + .8))
                            || level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())
                        throw new AssertionError("Straight arena approach obstructed at " + feet);
                }
                if ((AuthoredCatacombs.exits(level.getSeed(), 0, CatacombLayout.ROOT_Z) & 8) == 0)
                    throw new AssertionError("Arena approach has no side doorway into catacombs");
                var player = server.getPlayerList().getPlayers().getFirst();
                int center = CatacombLayout.ROOT_CENTER + AuthoredForge.DISTRICT_SPACING;
                player.teleportTo(level, center - 17.5, 73, center - 6.5, java.util.Set.of(), 90, 25, true);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 200));
                player.setNoGravity(true);
                Asterion.LOGGER.info("PASS: straight five-wide arena approach connects to the west catacomb doorway");
                Asterion.LOGGER.info("PASS: {} Forge upper-layer blocks preserved across positive/negative districts and both chunk orders; neighbouring structure untouched", checked);
            });
            context.waitTicks(35);
            context.takeScreenshot("forge-stair-wall-doorway");
        }
    }
}

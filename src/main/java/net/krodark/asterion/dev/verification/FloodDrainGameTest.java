package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.CatacombFloodState;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public final class FloodDrainGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                var state = CatacombFloodState.get(level);
                try {
                    var endsAt = CatacombFloodState.class.getDeclaredField("endsAt");
                    var nextStep = CatacombFloodState.class.getDeclaredField("nextStep");
                    var rise = CatacombFloodState.class.getDeclaredField("rise");
                    endsAt.setAccessible(true); nextStep.setAccessible(true); rise.setAccessible(true);
                    CatacombFloodState.start(level, Integer.MAX_VALUE);
                    check(endsAt.getLong(state) == level.getGameTime() + 3600, "Flood exceeds three minutes");
                    endsAt.setLong(state, level.getGameTime() + 100000);
                    CatacombFloodState.tick(level);
                    check(endsAt.getLong(state) == level.getGameTime() + 3600, "Old saved deadline was not bounded");
                    BlockPos base = new BlockPos(320, CatacombLayout.WATER_Y, 320);
                    BlockPos flooded = base.above(2);
                    level.setBlock(base, HeavyWater.WATER_BLOCK.defaultBlockState(), 2);
                    level.setBlock(flooded, HeavyWater.BLOCK.defaultBlockState(), 2);
                    rise.setInt(state, CatacombFloodState.MAX_RISE);
                    endsAt.setLong(state, level.getGameTime());
                    CatacombFloodState.tick(level);
                    check(!CatacombFloodState.isFlooding(level, flooded), "Expired flood kept rising");
                    int steps = 0;
                    while (state.riseSteps() > 0 && steps < 60) {
                        nextStep.setLong(state, level.getGameTime());
                        CatacombFloodState.tick(level);
                        check(nextStep.getLong(state) == level.getGameTime() + CatacombFloodState.DRAIN_STEP_TICKS,
                                "Drain used the slow rise interval");
                        steps++;
                    }
                    check(state.riseSteps() == 0, "Full flood did not drain within a minute");
                    CatacombFloodState.reconcile(level, level.getChunkAt(base), 0);
                    check(level.getBlockState(flooded).is(Blocks.AIR), "Temporary flood water remained");
                    check(level.getBlockState(base).is(HeavyWater.WATER_BLOCK), "Normal water was removed");
                    CatacombFloodState.start(level, CatacombFloodState.FLOOD_DURATION_TICKS);
                    for (BlockPos room : net.krodark.asterion.worldgen.AuthoredCatacombs.BRAZIER_ROOM_ORIGINS) {
                        BlockPos inside = room.offset(1, 4, 1);
                        BlockPos slab = inside.east();
                        BlockPos normal = inside.south();
                        level.setBlock(inside, HeavyWater.BLOCK.defaultBlockState(), 2);
                        level.setBlock(slab, net.krodark.asterion.fluid.HeavyWaterlogging.withFluid(
                                Blocks.OAK_SLAB.defaultBlockState(), HeavyWater.FLUID.getFlowing(8, false)), 2);
                        level.setBlock(normal, HeavyWater.WATER_BLOCK.defaultBlockState(), 2);
                        check(!CatacombFloodState.isFlooding(level, inside), "Brazier room reported as floodable");
                        CatacombFloodState.reconcile(level, level.getChunkAt(inside), CatacombFloodState.MAX_RISE);
                        CatacombFloodState.reconcile(level, level.getChunkAt(slab), CatacombFloodState.MAX_RISE);
                        CatacombFloodState.reconcile(level, level.getChunkAt(normal), CatacombFloodState.MAX_RISE);
                        CatacombFloodState.spread(level, CatacombFloodState.MAX_RISE);
                        check(level.getBlockState(inside).isAir(), "Old floodwater remained in Brazier room");
                        check(level.getBlockState(slab).is(Blocks.OAK_SLAB)
                                && level.getBlockState(slab).getFluidState().isEmpty(), "Flooded room slab was not dried safely: " + level.getBlockState(slab));
                        check(level.getBlockState(normal).is(HeavyWater.WATER_BLOCK), "Normal room water was removed");
                    }
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                } finally {
                    CatacombFloodState.setActive(level, false);
                }
            });
            Asterion.LOGGER.info("PASS: three-minute flood cap, saved deadline migration, one-minute drainage and normal water preservation");
            Asterion.LOGGER.info("PASS: all Cursed Brazier rooms exclude floodwater and dry existing tidal blocks");
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package net.krodark.asterion.dev.verification;

import com.mojang.serialization.JsonOps;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.CatacombFloodState;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.event.RareMazeEvents;
import net.krodark.asterion.event.RumbleSources;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterFatigue;
import net.krodark.asterion.fluid.TidalWaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real fluid ticks, immersion, chunk reconciliation and client resource/geometry checks in a disposable world. */
public final class HeavyWaterGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        AtomicInteger elapsed = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger highTideAt = new AtomicInteger(-1), stoppedAt = new AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        BlockPos flowing = new BlockPos(14, 122, 0), fixed = new BlockPos(-9, 122, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set day");
            server.runCommand("tp @a 2.5 128 13.5 180 30");
            server.runOnServer(mc -> {
                ServerLevel level = mc.overworld();
                for (int x = -12; x <= 24; x++) for (int z = -5; z <= 15; z++)
                    level.setBlock(new BlockPos(x, 121, z), Blocks.STONE.defaultBlockState(), 3);
                // Three-deep pool for immersion/fatigue, and an uncontained source for normal flow.
                for (int x = 0; x <= 5; x++) for (int z = 0; z <= 5; z++) for (int y = 122; y <= 124; y++)
                    level.setBlock(new BlockPos(x, y, z), x == 0 || x == 5 || z == 0 || z == 5
                            ? Blocks.GLASS.defaultBlockState() : HeavyWater.WATER_BLOCK.defaultBlockState(), 3);
                level.setBlock(flowing, HeavyWater.WATER_BLOCK.defaultBlockState(), 3);
                level.setBlock(fixed, HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, 4), 3);
                check(HeavyWater.BLOCK.getStateDefinition().getPossibleStates().size() == 8, "Flood block must have eight states");
                check(HeavyWater.FLUID.getStateDefinition().getPossibleStates().size() == 8, "Flood fluid must have eight states");
                for (int amount = 1; amount <= 8; amount++) {
                    BlockPos pos = new BlockPos(-11 + amount * 2, 122, 8);
                    var block = HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount);
                    level.setBlock(pos, block, 3);
                    var liquid = block.getFluidState();
                    check(liquid.is(FluidTags.WATER), "Custom water missing immersion tag");
                    check(Math.abs(liquid.getHeight(level, pos) - amount / 8.0) < 1e-6, "Wrong flood height");
                    check(liquid.getFlow(level, pos).equals(Vec3.ZERO), "Flood layer has a current");
                }
                check(HeavyWater.STILL.defaultFluidState().is(FluidTags.WATER), "Normal Heavy Water missing water tag");
                check(HeavyWater.STILL.getBucket() == HeavyWater.BUCKET, "Wrong bucket");
                for (var gate : Asterion.MAZESTEEL_GATE.getStateDefinition().getPossibleStates()) {
                    boolean open = gate.getValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN);
                    check(gate.getCollisionShape(level, fixed).isEmpty() == open, "Gate collision disagrees with open state");
                    check(gate.getShape(level, fixed).isEmpty() == open, "Open gate retains an invisible selection shape");
                }
                checkRumbleSources(level);
                checkFloodAndRarity(mc.getLevel(Asterion.ASTERION_LEVEL));
                long start = level.getGameTime() + 1;
                ServerTickEvents.END_LEVEL_TICK.register(ticking -> {
                    if (ticking != level || finished.get()) return;
                    int tick = (int)(level.getGameTime() - start);
                    if (tick < 0) return;
                    try {
                        var player = mc.getPlayerList().getPlayers().getFirst();
                        if (tick == 60) {
                            check(level.getFluidState(flowing.east()).getType() == HeavyWater.FLOWING, "Heavy Water failed to flow normally");
                            check(level.getBlockState(fixed.east()).isAir(), "Flood layer spread into its neighbor");
                        }
                        if (tick == 100) {
                            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                            player.teleportTo(2.5, 122.2, 2.5);
                        }
                        if (tick >= 101 && tick <= 530) player.setAirSupply(player.getMaxAirSupply());
                        if (tick == 120) {
                            check(HeavyWaterFatigue.swimmingInHeavyWater(player), "Heavy Water immersion not detected");
                            player.setDeltaMovement(.2, .1, 0);
                            player.travel(Vec3.ZERO);
                            check(player.getDeltaMovement().x < .16, "Heavy Water lacks additional horizontal resistance");
                            check(player.getDeltaMovement().y > 0 && player.getDeltaMovement().y < .08,
                                    "Heavy Water must slow ascent without forcing the swimmer down");
                            Asterion.LOGGER.info("PASS: open gate shapes and Heavy Water horizontal/ascent resistance");
                        }
                        if (tick == 300) check(!player.hasEffect(MobEffects.MINING_FATIGUE), "Fatigue starts too soon");
                        if (tick == 530) {
                            check(player.hasEffect(MobEffects.MINING_FATIGUE), "Prolonged swimming did not cause fatigue");
                            player.teleportTo(9.5, 123, 10.5);
                        }
                        if (tick == 680) {
                            check(!player.hasEffect(MobEffects.MINING_FATIGUE), "Fatigue did not recover on dry land");
                            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        }
                        var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                        if (tick >= 680 && highTideAt.get() < 0 && CatacombFloodState.get(maze).riseSteps() == 16) {
                            highTideAt.set(tick);
                            maze.setChunkForced(-20, -20, true);
                            maze.getChunk(-20, -20);
                        }
                        if (highTideAt.get() >= 0 && tick == highTideAt.get() + 120) {
                            check(maze.getBlockState(new BlockPos(320, 9, 320)).is(HeavyWater.BLOCK), "Live tide did not update loaded basin");
                            check(maze.getBlockState(new BlockPos(-320, 9, -320)).is(HeavyWater.BLOCK), "Reloaded chunk missed the shared high tide");
                            DeadSunEventSystem.stop(maze);
                            stoppedAt.set(tick);
                        }
                        if (stoppedAt.get() >= 0 && tick > stoppedAt.get() + 640 && CatacombFloodState.get(maze).riseSteps() == 0
                                && maze.getBlockState(new BlockPos(320, 7, 320)).is(HeavyWater.WATER_BLOCK)) {
                            check(maze.getBlockState(new BlockPos(320, 9, 320)).isAir(), "Live tide left water above the baseline");
                            maze.setChunkForced(20, 20, false);
                            maze.setChunkForced(-20, -20, false);
                            finished.set(true);
                        }
                        check(tick < 3400, "Tide failed to complete a rise/recede cycle within the bounded test window");
                        elapsed.set(tick);
                    } catch (Throwable error) { failure.set(error); finished.set(true); }
                });
            });
            context.waitFor(client -> elapsed.get() >= 70 || finished.get(), 1200);
            if (failure.get() != null) throw new AssertionError("Heavy Water integration failed", failure.get());
            context.runOnClient(client -> client.options.hideGui = true);
            context.takeScreenshot("heavy-water-eight-layers-and-flow");
            context.waitFor(client -> finished.get(), 3600);
            if (failure.get() != null) throw new AssertionError("Heavy Water integration failed", failure.get());
            Asterion.LOGGER.info("PASS: Heavy Water flow, eight stationary heights, bucket/tag registration, fatigue/recovery, flood reconciliation, persisted rarity and actual wall/roof rumble sources");
        }
    }

    private static void checkRumbleSources(ServerLevel level) {
        for (int y = 122; y <= 127; y++) level.setBlock(new BlockPos(22, y, 0), Blocks.STONE.defaultBlockState(), 3);
        var wall = RumbleSources.trace(level, new Vec3(19, 124.5, .5), new Vec3(24, 124.5, .5));
        check(wall != null && wall.normal().x == -1 && wall.position().x < 22, "Rubble must originate outside wall face");
        level.setBlock(new BlockPos(20, 127, 2), Blocks.STONE.defaultBlockState(), 3);
        var ceiling = RumbleSources.trace(level, new Vec3(20.5, 123, 2.5), new Vec3(20.5, 130, 2.5));
        check(ceiling != null && ceiling.normal().y == -1 && ceiling.position().y < 127, "Ceiling rubble must emerge below roof");
        check(RumbleSources.trace(level, new Vec3(0, 180, 0), new Vec3(0, 195, 0)) == null, "Rubble has a sky fallback");
        check(RumbleSources.find(level, new Vec3(0, 190, 0), new Random(123)) == null, "Off-limits fallback fabricated a surface");
    }

    private static void checkFloodAndRarity(ServerLevel maze) {
        var timing = RareMazeEvents.get(maze);
        long now = maze.getGameTime();
        check(timing.nextEclipseTick() - now >= 2L * RareMazeEvents.HOUR, "Eclipse is too common");
        check(timing.nextFloodTick() - now >= 3L * RareMazeEvents.HOUR, "Flood is too common");
        var saved = RareMazeEvents.CODEC.encodeStart(JsonOps.INSTANCE, timing).getOrThrow();
        var restored = RareMazeEvents.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        check(restored.nextFloodTick() == timing.nextFloodTick() && restored.nextEclipseTick() == timing.nextEclipseTick(), "Rare deadlines changed on save");
        check(!timing.ready(DeadSunEventSystem.ECLIPSE, now), "Natural eclipse bypassed quiet period");
        check(DeadSunEventSystem.trigger(maze, DeadSunEventSystem.ECLIPSE), "Forced eclipse should bypass rarity");
        DeadSunEventSystem.stop(maze);
        BlockPos[] bases = {new BlockPos(319, 7, 320), new BlockPos(320, 7, 320), new BlockPos(-320, 7, -320), new BlockPos(323, 7, 324)};
        for (BlockPos base : bases) {
            maze.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
            maze.setBlock(base, Blocks.WATER.defaultBlockState(), 3); // Existing saves migrate too.
            maze.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
            maze.setBlock(base.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos protectedBlock = bases[1].east().above();
        maze.setBlock(protectedBlock, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        for (int step = 1; step <= 16; step++) {
            for (BlockPos base : bases) CatacombFloodState.reconcile(maze, maze.getChunkAt(base), step);
            int surfaceY = step <= 8 ? 8 : 9;
            for (BlockPos base : bases) {
                BlockPos surface = new BlockPos(base.getX(), surfaceY, base.getZ());
                check(maze.getFluidState(surface).getType() == HeavyWater.FLUID, "Flood surface missing");
                check(Math.abs(surfaceY + maze.getFluidState(surface).getOwnHeight() - (8 + step / 8.0)) < 1e-6,
                        "Flood desynchronized across chunk/negative-coordinate boundary");
            }
            check(maze.getBlockState(protectedBlock).is(Blocks.DIAMOND_BLOCK), "Flood destroyed a player block");
        }
        for (BlockPos base : bases) {
            CatacombFloodState.reconcile(maze, maze.getChunkAt(base), 0);
            check(maze.getBlockState(base).is(HeavyWater.WATER_BLOCK), "Receding tide did not restore normal water");
            check(maze.getBlockState(base.above()).isAir() && maze.getBlockState(base.above(2)).isAir(), "Flood did not drain");
        }
        check(DeadSunEventSystem.trigger(maze, DeadSunEventSystem.FLOOD), "Forced flood should bypass rarity");
        check(CatacombFloodState.isFlooding(maze, bases[0]), "Flood signal missing");
        var encoded = CatacombFloodState.CODEC.encodeStart(JsonOps.INSTANCE, CatacombFloodState.get(maze)).getOrThrow();
        check(CatacombFloodState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().riseSteps() == 0, "Flood persistence mismatch");
        DeadSunEventSystem.stop(maze);
        check(!CatacombFloodState.isFlooding(maze, bases[0]), "Stopped flood still active");
        maze.setChunkForced(20, 20, true);
        check(DeadSunEventSystem.trigger(maze, DeadSunEventSystem.FLOOD), "Failed to start live tide test");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}

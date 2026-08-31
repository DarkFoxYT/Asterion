package net.krodark.asterion.dev.verification;

import com.mojang.serialization.JsonOps;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.CatacombFloodState;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.krodark.asterion.fluid.TidalWaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class HeavyWaterloggingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        AtomicInteger elapsed = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set day");
            server.runCommand("tp @a 0.5 122 5.5 180 20");
            server.runOnServer(mc -> {
                ServerLevel level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                CatacombProtectionCheck.run(mc, player);
                BlockPos decorationPos = new BlockPos(22, 122, 0);
                level.setBlock(decorationPos.below(), Blocks.DIRT.defaultBlockState(), 3);
                for (var block : new net.minecraft.world.level.block.Block[]{Asterion.SKELETON, Asterion.LABYRINTH_VINE,
                        Asterion.SHORT_GRASS, Asterion.ANCIENT_MOSS, Asterion.ANCIENT_MOSS_CARPET, Asterion.BARREL_DOOR}) {
                    BlockState dry = block.defaultBlockState();
                    if (block == Asterion.LABYRINTH_VINE) dry = dry.setValue(net.krodark.asterion.block.LabyrinthVineBlock.FACING, Direction.UP);
                    check(!dry.getValue(BlockStateProperties.WATERLOGGED), "Decoration defaults to wet");
                    level.setBlock(decorationPos, dry, 2);
                    var container = (net.minecraft.world.level.block.SimpleWaterloggedBlock)block;
                    check(container.placeLiquid(level, decorationPos, dry, net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()), "Vanilla water rejected");
                    check(level.getFluidState(decorationPos).isSource(), "Vanilla water not exposed by decoration");
                    for (int amount = 1; amount <= 9; amount++) {
                        level.setBlock(decorationPos, dry, 2);
                        check(HeavyWaterlogging.fill(level, decorationPos, dry, HeavyWaterlogging.fluid(amount)), "Heavy Water rejected by decoration");
                        check(HeavyWaterlogging.amount(level.getBlockState(decorationPos)) == amount, "Decoration lost water layer");
                        var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, level.getBlockState(decorationPos)).getOrThrow();
                        check(HeavyWaterlogging.amount(BlockState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()) == amount, "Decoration water did not survive save");
                    }
                }
                level.setBlock(decorationPos, Blocks.AIR.defaultBlockState(), 2);
                Asterion.LOGGER.info("PASS: corpse, vines, grass, moss and barrel door support vanilla water and all Heavy Water levels");
                for (int x = -8; x <= 20; x++) for (int z = -4; z <= 10; z++)
                    level.setBlock(new BlockPos(x, 121, z), Blocks.STONE.defaultBlockState(), 3);
                BlockState[] candidates = {
                        Blocks.STONE_STAIRS.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState(),
                        Blocks.IRON_BARS.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                        Blocks.LANTERN.defaultBlockState(), Asterion.MAZESTEEL_GATE.defaultBlockState(),
                        Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true)
                };
                for (int i = 0; i < candidates.length; i++) {
                    BlockPos pos = new BlockPos(i * 2, 122, 0);
                    level.setBlock(pos, candidates[i], 3);
                    check(((BucketItem)HeavyWater.BUCKET).emptyContents(player, level, pos, null), "Bucket failed to waterlog " + candidates[i]);
                    check(level.getBlockState(pos).is(candidates[i].getBlock()), "Bucket replaced its container");
                    check(level.getFluidState(pos).getType() == HeavyWater.STILL, "Waterlogged block lost Heavy Water identity");
                    if (candidates[i].hasProperty(BlockStateProperties.LIT))
                        check(!level.getBlockState(pos).getValue(BlockStateProperties.LIT), "Waterlogging did not extinguish campfire");
                    ItemStack picked = ((BucketPickup)candidates[i].getBlock()).pickupBlock(player, level, pos, level.getBlockState(pos));
                    check(picked.is(HeavyWater.BUCKET), "Waterlogged bucket pickup returned ordinary water");
                    check(!level.getBlockState(pos).getValue(BlockStateProperties.WATERLOGGED), "Pickup did not drain container");
                }
                BlockPos solid = new BlockPos(16, 122, 0);
                BlockPos circuit = new BlockPos(18, 122, 8);
                for (var block : new net.minecraft.world.level.block.Block[]{Blocks.REDSTONE_WIRE,
                        Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
                        Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.STONE_PRESSURE_PLATE,
                        Blocks.OAK_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
                        Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.TRIPWIRE, Blocks.TRIPWIRE_HOOK,
                        Blocks.DAYLIGHT_DETECTOR, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL}) {
                    for (Direction side : Direction.values())
                        level.setBlock(circuit.relative(side), Blocks.STONE.defaultBlockState(), 2);
                    for (int amount = 1; amount <= 9; amount++) {
                        BlockState dry = block.defaultBlockState();
                        level.setBlock(circuit, dry, 2);
                        check(HeavyWaterlogging.fill(level, circuit, dry, HeavyWaterlogging.fluid(amount)), "Circuit rejected heavy water: " + block);
                        level.updateNeighborsAt(circuit.above(), Blocks.STONE);
                        if (block == Blocks.TRIPWIRE_HOOK)
                            net.minecraft.world.level.block.TripWireHookBlock.calculateState(level, circuit, level.getBlockState(circuit), false, true, -1, null);
                        BlockState wet = level.getBlockState(circuit);
                        check(wet.is(block) && HeavyWaterlogging.amount(wet) == amount, "Circuit lost block/water on neighbor update: " + block);
                        var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, wet).getOrThrow();
                        check(BlockState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow() == wet, "Circuit water failed save round-trip");
                    }
                    level.setBlock(circuit, block.defaultBlockState(), 2);
                    check(((BucketItem)HeavyWater.BUCKET).emptyContents(player, level, circuit, null), "Bucket destroyed circuit: " + block);
                    check(level.getBlockState(circuit).is(block), "Bucket replaced circuit: " + block);
                    check(((BucketPickup)block).pickupBlock(player, level, circuit, level.getBlockState(circuit)).is(HeavyWater.BUCKET), "Circuit pickup failed: " + block);
                }
                Asterion.LOGGER.info("PASS: 18 redstone components retain all Heavy Water layers, neighbor updates, saved state and bucket pickup");
                level.setBlock(circuit, Blocks.REDSTONE_WIRE.defaultBlockState(), 2);
                check(((BucketItem)Items.WATER_BUCKET).emptyContents(player, level, circuit, null), "Ordinary water bucket failed");
                check(!level.getBlockState(circuit).is(Blocks.REDSTONE_WIRE), "Changed ordinary water's redstone behavior");
                level.setBlock(solid, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE), 3);
                check(!((BucketItem)HeavyWater.BUCKET).emptyContents(player, level, solid, null), "Waterlogged a double slab");
                check(level.getBlockState(solid).is(Blocks.STONE_SLAB), "Double slab was destroyed");

                // A real bucket use must target the container, not the adjacent block.
                BlockPos aimed = new BlockPos(0, 122, 0);
                player.teleportTo(.5, 122, 3.5);
                player.setYRot(180); player.setXRot(30);
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HeavyWater.BUCKET));
                HeavyWater.BUCKET.use(level, player, InteractionHand.MAIN_HAND);
                check(HeavyWaterlogging.amount(level.getBlockState(aimed)) == HeavyWaterlogging.NORMAL, "Bucket use missed the clicked stairs");

                BlockPos placed = new BlockPos(-4, 122, 0);
                for (int amount = 1; amount <= 8; amount++) {
                    level.setBlock(placed, HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount), 3);
                    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_STAIRS));
                    var hit = new BlockHitResult(Vec3.atCenterOf(placed.below()).add(0, .5, 0), Direction.UP, placed.below(), false);
                    var placement = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                    ((BlockItem)Items.STONE_STAIRS).place(placement);
                    BlockState state = level.getBlockState(placed);
                    check(state.is(Blocks.STONE_STAIRS), "Stairs not placed in a flood layer");
                    check(HeavyWaterlogging.amount(state) == amount, "Placement lost the partial flood height");
                    check(Math.abs(state.getFluidState().getOwnHeight() - amount / 8.0) < 1e-6, "Waterlogged surface has wrong height");
                    var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
                    check(BlockState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow() == state, "Waterlogged height failed save round-trip");
                    if (amount < 8) check(((BucketPickup)state.getBlock()).pickupBlock(player, level, placed, state).isEmpty(), "Partial layer yielded a whole bucket");
                }

                // Flow, independent of bucket use and placement, must waterlog nearby containers.
                BlockPos placedWire = new BlockPos(-6, 122, 0);
                for (int amount = 1; amount <= 8; amount++) {
                    level.setBlock(placedWire, HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount), 3);
                    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.REDSTONE));
                    var hit = new BlockHitResult(Vec3.atCenterOf(placedWire.below()).add(0, .5, 0), Direction.UP, placedWire.below(), false);
                    ((BlockItem)Items.REDSTONE).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)));
                    check(level.getBlockState(placedWire).is(Blocks.REDSTONE_WIRE)
                            && HeavyWaterlogging.amount(level.getBlockState(placedWire)) == amount,
                            "Placing redstone lost the flood layer");
                }
                BlockPos flow = new BlockPos(8, 122, 6);
                level.setBlock(flow, HeavyWater.WATER_BLOCK.defaultBlockState(), 3);
                level.setBlock(flow.east(), Blocks.STONE_SLAB.defaultBlockState(), 3);
                BlockPos wire = flow.west();
                level.setBlock(wire, Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
                level.setBlock(wire.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                checkFloodContainers(mc.getLevel(Asterion.ASTERION_LEVEL));
                long start = level.getGameTime();
                ServerTickEvents.END_LEVEL_TICK.register(ticking -> {
                    if (ticking != level || elapsed.get() >= 100) return;
                    int tick = (int)(level.getGameTime() - start);
                    try {
                        if (tick >= 100) {
                            check(level.getBlockState(flow.east()).is(Blocks.STONE_SLAB), "Flow replaced slab");
                            check(level.getBlockState(wire).is(Blocks.REDSTONE_WIRE), "Flow destroyed redstone dust");
                            check(HeavyWaterlogging.amount(level.getBlockState(wire)) == HeavyWaterlogging.NORMAL, "Flow failed to waterlog dust");
                            check(level.getBlockState(wire).getValue(BlockStateProperties.POWER) == 15, "Flooded dust stopped conducting power");
                            check(HeavyWaterlogging.amount(level.getBlockState(flow.east())) == HeavyWaterlogging.NORMAL, "Flow did not waterlog slab");
                            check(level.getBlockState(placed).is(Blocks.STONE_STAIRS), "Scheduled ticks destroyed logged stairs");
                        }
                        elapsed.set(tick);
                    } catch (Throwable error) { failure.set(error); elapsed.set(100); }
                });
            });
            server.runCommand("tp @a 2.5 124 7.5 180 25");
            context.waitFor(client -> elapsed.get() >= 100, 1600);
            if (failure.get() != null) throw new AssertionError("Waterlogging regression", failure.get());
            context.runOnClient(client -> client.options.hideGui = true);
            context.takeScreenshot("heavy-waterlogged-stairs-gates-slabs");
            Asterion.LOGGER.info("PASS: Heavy Water bucket targeting/pickup, natural flow waterlogging, all eight placed/flooded heights, state persistence, protected blocks, extinguishing and recession");
        }
    }
    private static void checkFloodContainers(ServerLevel maze) {
        BlockState[] shapes = {Blocks.STONE_STAIRS.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState(),
                Blocks.IRON_BARS.defaultBlockState(), Asterion.MAZESTEEL_GATE.defaultBlockState(),
                Blocks.REDSTONE_WIRE.defaultBlockState(), Blocks.REPEATER.defaultBlockState(),
                Blocks.COMPARATOR.defaultBlockState()};
        BlockPos base = new BlockPos(320, 7, 320);
        for (int i = 0; i < shapes.length; i++) {
            BlockPos pos = base.east(i);
            maze.setBlock(pos, HeavyWater.WATER_BLOCK.defaultBlockState(), 3);
            if (shapes[i].getBlock() instanceof net.krodark.asterion.block.HeavyWaterRedstone)
                maze.setBlock(pos, HeavyWaterlogging.withFluid(Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), HeavyWater.STILL.defaultFluidState()), 3);
            maze.setBlock(pos.above(), shapes[i], 3);
        }
        BlockPos preWet = base.east(9).above();
        maze.setBlock(preWet.below(), HeavyWater.WATER_BLOCK.defaultBlockState(), 3);
        maze.setBlock(preWet, Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 3);
        for (int step = 1; step <= 8; step++) {
            CatacombFloodState.reconcile(maze, maze.getChunkAt(base), step);
            for (int i = 0; i < shapes.length; i++) {
                BlockState state = maze.getBlockState(base.east(i).above());
                check(state.is(shapes[i].getBlock()), "Flood destroyed a container");
                check(HeavyWaterlogging.amount(state) == step, "Flood skipped partial waterlogging");
            }
        }
        for (int step = 7; step >= 0; step--) {
            CatacombFloodState.reconcile(maze, maze.getChunkAt(base), step);
            for (int i = 0; i < shapes.length; i++) {
                BlockState state = maze.getBlockState(base.east(i).above());
                check(state.is(shapes[i].getBlock()), "Recession destroyed a container");
                check(HeavyWaterlogging.amount(state) == step, "Waterlogged tide did not recede by layers");
            }
        }
        check(maze.getBlockState(preWet).getValue(BlockStateProperties.WATERLOGGED), "Recession removed preexisting water");
    }
    private static void check(boolean pass, String reason) { if (!pass) throw new AssertionError(reason); }
}

package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.BiConsumer;

/** Temporary arena floor details; elevated brazier routes await the authored map. */
public final class CatacombArena {
    private static final java.util.Map<java.util.UUID, Motion> MOTION = new java.util.HashMap<>();
    private static final int FLOOR = 36;
    private static final int[][] POOLS = {{7, 7}, {-7, 7}, {7, -7}, {-7, -7},
            {20, 10}, {-20, 10}, {20, -10}, {-20, -10}, {10, 20}, {-10, 20}, {10, -20}, {-10, -20}};

    private CatacombArena() { }

    public static boolean puddle(int x, int z) {
        for (int[] center : POOLS) {
            int dx = x - center[0], dz = z - center[1];
            if (dx * dx * 4 + dz * dz * 7 <= 42) return true;
        }
        return false;
    }

    public static BlockPos brazier(Direction direction) {
        return new BlockPos(direction.getStepX() * 25, FLOOR + 7, direction.getStepZ() * 25)
                .relative(direction.getClockWise(), 6);
    }

    public static BlockPos lamenter(Direction direction) {
        return brazier(direction).above(2).relative(direction);
    }

    public static void build(BiConsumer<BlockPos, BlockState> place) {
        // Elevated brazier/platform puzzle is reserved for the authored arena map.
        // Self-contained ceiling leaks survive without flooding the arena floor.
        for (int[] pool : POOLS) {
            int x = pool[0], z = pool[1];
            int y = FLOOR + 18 + Math.max(0, (34 - (int)Math.sqrt(x * x + z * z)) / 4);
            place.accept(new BlockPos(x, y - 1, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
            place.accept(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
            place.accept(new BlockPos(x, y + 1, z), Asterion.ANCIENT_BRICKS.defaultBlockState());
            for (Direction side : Direction.Plane.HORIZONTAL)
                place.accept(new BlockPos(x, y, z).relative(side), Asterion.ANCIENT_BRICKS.defaultBlockState());
            place.accept(new BlockPos(x, y - 2, z), Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN));
        }
    }

    public static boolean protectedBlock(BlockPos pos, BlockState state) {
        return pos.getY() >= FLOOR && pos.getY() <= FLOOR + 11
                && Math.abs(pos.getX()) <= 30 && Math.abs(pos.getZ()) <= 30
                && (state.is(Asterion.MAZESTEEL_BLOCK) || state.is(Asterion.MAZESTEEL_CHAIN)
                || state.is(Asterion.GREEK_BRAZIER) || state.is(Asterion.LAMENTER)
                || state.is(Asterion.SLICK_CATACOMB_STONE));
    }

    public static void tick(net.minecraft.server.level.ServerLevel level) {
        MOTION.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying
                    || !player.onGround() || !player.isInWater() || player.isShiftKeyDown()
                    || !level.getBlockState(player.blockPosition().below()).is(Asterion.SLICK_CATACOMB_STONE)) {
                MOTION.remove(player.getUUID());
                continue;
            }
            var previous = MOTION.get(player.getUUID());
            var movement = previous == null ? net.minecraft.world.phys.Vec3.ZERO
                    : player.position().subtract(previous.position).multiply(1, 0, 1);
            if (movement.lengthSqr() > 1) { MOTION.remove(player.getUUID()); continue; }
            if (previous != null && previous.drift.lengthSqr() > 0.004
                    && (level.getGameTime() & 1) == 0) {
                // Vanilla water bypasses ground friction. Preserve a little momentum when a
                // grounded player brakes or reverses direction in a puddle; sneaking gives grip.
                var slip = previous.drift.scale(0.72).add(movement.scale(0.28));
                if (slip.lengthSqr() > 0.16) slip = slip.normalize().scale(0.4);
                if (slip.subtract(movement).lengthSqr() > 0.0016) {
                    player.setDeltaMovement(slip.x, player.getDeltaMovement().y, slip.z);
                    player.hurtMarked = true;
                }
            }
            var drift = previous == null ? movement : previous.drift.scale(0.35).add(movement.scale(0.65));
            MOTION.put(player.getUUID(), new Motion(player.position(), drift));
        }
    }

    public static void clear() { MOTION.clear(); }
    private record Motion(net.minecraft.world.phys.Vec3 position, net.minecraft.world.phys.Vec3 drift) { }
}

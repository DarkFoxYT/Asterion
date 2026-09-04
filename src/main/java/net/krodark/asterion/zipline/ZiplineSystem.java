package net.krodark.asterion.zipline;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative sliding directly along ordinary placed chain blocks. */
public final class ZiplineSystem {
    private static final Map<UUID, Ride> RIDERS = new HashMap<>();
    private record Ride(BlockPos chain, Vec3 point, int direction) { }
    private ZiplineSystem() { }

    public static void begin(Player player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(7D)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) begin(player, hit.getBlockPos());
    }

    public static void begin(Player player, BlockPos position) {
        if (RIDERS.remove(player.getUUID()) != null) return;
        BlockState state = player.level().getBlockState(position);
        if (!isHorizontalChain(state)) return;
        Vec3 axis = axisVector(state.getValue(BlockStateProperties.AXIS));
        int direction = player.getLookAngle().dot(axis) < 0D ? -1 : 1;
        RIDERS.put(player.getUUID(), new Ride(position.immutable(), position.getCenter(), direction));
    }

    public static void stop(Player player) { RIDERS.remove(player.getUUID()); }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Ride>> iterator = RIDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Ride> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Ride ride = entry.getValue();
            if (player == null || player.isShiftKeyDown()) {
                iterator.remove();
                continue;
            }
            BlockState state = player.level().getBlockState(ride.chain());
            if (!isHorizontalChain(state)) {
                iterator.remove();
                continue;
            }
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            Vec3 along = axisVector(axis);
            var input = player.getLastClientInput();
            int inputDirection = input.forward() == input.backward() ? 0 : input.forward() ? 1 : -1;
            double lookProjection = player.getLookAngle().dot(along);
            int facingDirection = Math.abs(lookProjection) > .12D
                    ? (lookProjection < 0D ? -1 : 1) : ride.direction();
            // W moves where the player faces, S reverses. Looking across the cable slows
            // travel but never makes the forward key choose an arbitrary axis direction.
            double alignment = Math.max(.18D, Math.abs(lookProjection));
            double signedSpeed = .34D * inputDirection * facingDirection * alignment;
            Vec3 nextPoint = ride.point().add(along.scale(signedSpeed));
            BlockPos nextBlock = ride.chain();
            double local = coordinate(nextPoint.subtract(ride.chain().getCenter()), axis);
            if (local > .5D || local < -.5D) {
                Direction direction = Direction.fromAxisAndDirection(axis,
                        local > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
                BlockPos candidate = ride.chain().relative(direction);
                if (!isHorizontalChain(player.level().getBlockState(candidate))) {
                    double best = .12D;
                    BlockPos turn = null;
                    for (Direction option : Direction.values()) {
                        BlockPos neighbor = ride.chain().relative(option);
                        if (!isHorizontalChain(player.level().getBlockState(neighbor))) continue;
                        double score = player.getLookAngle().dot(Vec3.atLowerCornerOf(option.getUnitVec3i()));
                        if (score > best) { best = score; turn = neighbor; }
                    }
                    if (turn == null) { iterator.remove(); continue; }
                    nextBlock = turn;
                    nextPoint = turn.getCenter();
                } else nextBlock = candidate;
            }
            Vec3 playerPosition = nextPoint.subtract(0D, player.getBbHeight() + .18D, 0D);
            player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
            player.setDeltaMovement(along.scale(signedSpeed));
            player.fallDistance = 0;
            entry.setValue(new Ride(nextBlock, nextPoint, facingDirection));
        }
    }

    public static boolean isChain(BlockState state) {
        return state.getBlock() instanceof ChainBlock && state.hasProperty(BlockStateProperties.AXIS);
    }
    public static boolean isHorizontalChain(BlockState state) {
        return isChain(state) && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }
    private static Vec3 axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1D, 0D, 0D);
            case Y -> new Vec3(0D, 1D, 0D);
            case Z -> new Vec3(0D, 0D, 1D);
        };
    }
    private static double coordinate(Vec3 vector, Direction.Axis axis) {
        return switch (axis) { case X -> vector.x; case Y -> vector.y; case Z -> vector.z; };
    }
}

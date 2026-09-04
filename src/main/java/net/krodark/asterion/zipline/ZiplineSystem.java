package net.krodark.asterion.zipline;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
    private record Ride(BlockPos chain, Vec3 point, int direction, boolean hadNoGravity) { }
    private ZiplineSystem() { }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> stop(oldPlayer));
    }

    public static void begin(Player player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(7D)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) begin(player, hit.getBlockPos());
    }

    public static void begin(Player player, BlockPos position) {
        Ride current = RIDERS.remove(player.getUUID());
        if (current != null) {
            restoreGravity(player, current);
            return;
        }
        BlockState state = player.level().getBlockState(position);
        if (!isHorizontalChain(state)) return;
        Vec3 axis = axisVector(state.getValue(BlockStateProperties.AXIS));
        int direction = player.getLookAngle().dot(axis) < 0D ? -1 : 1;
        Vec3 point = position.getCenter();
        Ride ride = new Ride(position.immutable(), point, direction, player.isNoGravity());
        RIDERS.put(player.getUUID(), ride);
        attach(player, point, Vec3.ZERO);
    }

    public static void stop(Player player) {
        Ride ride = RIDERS.remove(player.getUUID());
        if (ride != null) restoreGravity(player, ride);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Ride>> iterator = RIDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Ride> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Ride ride = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.isShiftKeyDown() || !player.isAlive() || player.isSpectator()) {
                restoreGravity(player, ride);
                iterator.remove();
                continue;
            }
            BlockState state = player.level().getBlockState(ride.chain());
            if (!isHorizontalChain(state)) {
                restoreGravity(player, ride);
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
                    if (turn == null) {
                        restoreGravity(player, ride);
                        iterator.remove();
                        continue;
                    }
                    nextBlock = turn;
                    nextPoint = turn.getCenter();
                } else nextBlock = candidate;
            }
            attach(player, nextPoint, along.scale(signedSpeed));
            entry.setValue(new Ride(nextBlock, nextPoint, facingDirection, ride.hadNoGravity()));
        }
    }

    private static void attach(Player player, Vec3 chainPoint, Vec3 velocity) {
        // Gravity must not compete with the server-authoritative hanging position. Keep
        // the player centered directly below the cable and allow motion only along it.
        player.setNoGravity(true);
        Vec3 playerPosition = chainPoint.subtract(0D, player.getBbHeight() + .18D, 0D);
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
    }

    private static void restoreGravity(Player player, Ride ride) {
        player.setNoGravity(ride.hadNoGravity());
        player.fallDistance = 0;
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

package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Six-way redstone winch. Connected gate panels are discovered without configuration and
 * advanced one projected layer at a time in the direction the winch was placed.
 */
public final class WinchBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int FASTEST_STEP_DELAY = 2;
    private static final int SLOWEST_STEP_DELAY = 30;

    public WinchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        // The open state belongs to the winch, not to the panels themselves. If the
        // controller is removed, fail closed so an open gate cannot be left floating.
        boolean closedAny = false;
        for (BlockPos gatePos : findConnectedGates(level, pos)) {
            if (level.dimension().equals(Asterion.ASTERION_LEVEL)
                    && net.krodark.asterion.worldgen.MinotaurArenaEntrances.isGate(gatePos)) continue;
            BlockState gate = level.getBlockState(gatePos);
            if (!gate.getValue(DirectionalGateBlock.OPEN)) continue;
            level.setBlock(gatePos, gate.setValue(DirectionalGateBlock.OPEN, false), Block.UPDATE_ALL);
            closedAny = true;
        }
        if (closedAny) level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.65F, 0.7F);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        boolean powerChanged = powered != state.getValue(POWERED);
        if (powerChanged) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
        // Also wake on analog strength changes while the winch remains powered.
        // Gate updates themselves are ignored here so they cannot bypass the chosen delay.
        if (powerChanged || neighborBlock != Asterion.MAZESTEEL_GATE)
            level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            state = state.setValue(POWERED, powered);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        List<BlockPos> gates = findConnectedGates(level, pos);
        if (gates.isEmpty()) return;

        Direction direction = state.getValue(FACING);
        List<BlockPos> candidates = gates.stream()
                // Arena gates belong to the encounter, including when idle; nearby winches must not trap entrants.
                .filter(gatePos -> !level.dimension().equals(Asterion.ASTERION_LEVEL)
                        || !net.krodark.asterion.worldgen.MinotaurArenaEntrances.isGate(gatePos))
                .filter(gatePos -> level.getBlockState(gatePos).getValue(DirectionalGateBlock.OPEN) != powered)
                .toList();
        if (candidates.isEmpty()) return;

        Comparator<BlockPos> alongWinch = Comparator.comparingInt(gatePos -> projection(pos, gatePos, direction));
        // Inverted: opening travels against the placed face; closing returns the other way.
        BlockPos layerAnchor = (powered
                ? candidates.stream().max(alongWinch)
                : candidates.stream().min(alongWinch)).orElseThrow();
        int activeLayer = projection(pos, layerAnchor, direction);

        boolean changed = false;
        for (BlockPos gatePos : candidates) {
            if (projection(pos, gatePos, direction) != activeLayer) continue;
            BlockState gate = level.getBlockState(gatePos);
            level.setBlock(gatePos, gate.setValue(DirectionalGateBlock.OPEN, powered), Block.UPDATE_ALL);
            changed = true;
        }

        if (changed) {
            level.playSound(null, pos,
                    powered ? SoundEvents.CHAIN_PLACE : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.55F, powered ? 0.8F + random.nextFloat() * 0.12F : 0.7F);
            int signalStrength = level.getBestNeighborSignal(pos);
            level.scheduleTick(pos, this, stepDelay(powered, signalStrength));
        }
    }

    private static int stepDelay(boolean powered, int signalStrength) {
        if (!powered) return FASTEST_STEP_DELAY;
        int clampedSignal = Math.max(1, Math.min(15, signalStrength));
        return SLOWEST_STEP_DELAY
                - (clampedSignal - 1) * (SLOWEST_STEP_DELAY - FASTEST_STEP_DELAY) / 14;
    }

    private static int projection(BlockPos origin, BlockPos gate, Direction direction) {
        return (gate.getX() - origin.getX()) * direction.getStepX()
                + (gate.getY() - origin.getY()) * direction.getStepY()
                + (gate.getZ() - origin.getZ()) * direction.getStepZ();
    }

    private static List<BlockPos> findConnectedGates(Level level, BlockPos winchPos) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            BlockPos adjacent = winchPos.relative(direction);
            if (level.getBlockState(adjacent).is(Asterion.MAZESTEEL_GATE)) open.add(adjacent);
        }

        while (!open.isEmpty()) {
            BlockPos current = open.removeFirst();
            if (!visited.add(current) || !level.getBlockState(current).is(Asterion.MAZESTEEL_GATE)) continue;
            result.add(current.immutable());
            for (Direction direction : Direction.values()) open.addLast(current.relative(direction));
        }
        return result;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

}

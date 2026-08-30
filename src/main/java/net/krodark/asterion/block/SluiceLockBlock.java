package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The three lamps above the valves encode ON / OFF / ON. Solving latches the sluice open. */
public final class SluiceLockBlock extends Block {
    public SluiceLockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !state.getValue(BlockStateProperties.LIT)) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(BlockStateProperties.LIT)) return;
        level.scheduleTick(pos, this, 20);
        Direction forward = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction right = forward.getClockWise();
        for (int index = -1; index <= 1; index++) {
            BlockPos valve = pos.relative(forward, 3).relative(right, index * 2).below(2);
            if (!level.getChunkSource().hasChunk(valve.getX() >> 4, valve.getZ() >> 4)) return;
            BlockState lever = level.getBlockState(valve);
            if (!lever.is(Blocks.LEVER) || lever.getValue(BlockStateProperties.POWERED) != (index != 0)) return;
        }
        // Check the entire gate before changing anything, avoiding chunk loads and partial solutions.
        for (int across = -1; across <= 1; across++) {
            BlockPos edge = pos.relative(right, across);
            if (!level.getChunkSource().hasChunk(edge.getX() >> 4, edge.getZ() >> 4)) return;
        }
        for (int across = -1; across <= 1; across++) for (int down = 1; down <= 4; down++) {
            BlockPos gatePos = pos.relative(right, across).below(down);
            BlockState gate = level.getBlockState(gatePos);
            if (gate.is(Asterion.MAZESTEEL_GATE))
                level.setBlock(gatePos, gate.setValue(DirectionalGateBlock.OPEN, true), Block.UPDATE_ALL);
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.65F);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING))); }
}

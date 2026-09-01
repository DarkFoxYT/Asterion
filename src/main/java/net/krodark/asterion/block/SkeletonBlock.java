package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A floor skeleton that can only face one of the four horizontal directions. */
public final class SkeletonBlock extends BaseEntityBlock implements WaterloggedDecoration {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SOUTH_SHAPE = box(2.0D, 0.0D, 0.0D, 14.0D, 2.5D, 16.0D);
    private static final VoxelShape EAST_WEST_SHAPE = box(0.0D, 0.0D, 2.0D, 16.0D, 2.5D, 14.0D);

    public SkeletonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH_SHAPE : EAST_WEST_SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;

        Direction facing = state.getValue(FACING);
        double forward = (random.nextDouble() - 0.5D) * 1.7D;
        // Spawn beside the bones instead of inside their collision/model volume.
        double sideways = (random.nextBoolean() ? 1.0D : -1.0D) * (0.58D + random.nextDouble() * 0.22D);
        double x = pos.getX() + 0.5D + facing.getStepX() * forward - facing.getStepZ() * sideways;
        double y = pos.getY() + 0.32D + random.nextDouble() * 0.48D;
        double z = pos.getZ() + 0.5D + facing.getStepZ() * forward + facing.getStepX() * sideways;
        double outwardX = -facing.getStepZ() * Math.signum(sideways);
        double outwardZ = facing.getStepX() * Math.signum(sideways);
        double drift = (random.nextDouble() - 0.5D) * 0.012D;
        level.addParticle(Asterion.FLY, x, y, z,
                outwardX * 0.026D + facing.getStepX() * drift,
                0.012D + random.nextDouble() * 0.012D,
                outwardZ * 0.026D + facing.getStepZ() * drift);
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkeletonBlockEntity(pos, state);
    }
}

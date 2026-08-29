package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A six-direction chain-vine whose exposed growth tip becomes a luminous bone bulb. */
public final class LabyrinthVineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty END = BooleanProperty.create("end");

    public LabyrinthVineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(END, true));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        // A freshly extended segment is always the new exposed tip. Neighbor updates will
        // convert the previous tip to a middle segment after this block enters the world.
        return defaultBlockState().setValue(FACING, direction).setValue(END, true);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTicks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        Direction supportDirection = state.getValue(FACING).getOpposite();
        if (direction == supportDirection && !canSurvive(state, level, pos))
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        // A neighbor is a child only when it points away from this segment. This lets a
        // chain turn corners without rewriting the parent's attachment direction.
        boolean hasChild = false;
        for (Direction candidate : Direction.values()) {
            BlockState adjacent = candidate == direction ? neighborState
                    : level.getBlockState(pos.relative(candidate));
            if (adjacent.is(Asterion.LABYRINTH_VINE)
                    && adjacent.getValue(FACING) == candidate) {
                hasChild = true;
                break;
            }
        }
        return state.setValue(END, !hasChild);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction growthDirection = state.getValue(FACING);
        BlockPos supportPos = pos.relative(growthDirection.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return support.is(Asterion.LABYRINTH_VINE)
                || Block.canSupportCenter(level, supportPos, growthDirection);
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
        builder.add(FACING, END);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(END) || random.nextInt(18) != 0) return;

        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.34D
                + (random.nextDouble() - 0.5D) * 0.5D;
        double y = pos.getY() + 0.5D + facing.getStepY() * 0.34D
                + (random.nextDouble() - 0.5D) * 0.5D;
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.34D
                + (random.nextDouble() - 0.5D) * 0.5D;
        level.addParticle(Asterion.FIREFLY, x, y, z,
                (random.nextDouble() - 0.5D) * 0.018D,
                (random.nextDouble() - 0.35D) * 0.014D,
                (random.nextDouble() - 0.5D) * 0.018D);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> box(0, 5, 5, 16, 11, 11);
            case Y -> box(5, 0, 5, 11, 16, 11);
            case Z -> box(5, 5, 0, 11, 11, 16);
        };
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabyrinthVineBlockEntity(pos, state);
    }
}

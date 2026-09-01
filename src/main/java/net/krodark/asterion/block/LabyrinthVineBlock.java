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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A deliberately simple vertical vine: it either grows up from a floor or down from a ceiling. */
public final class LabyrinthVineBlock extends BaseEntityBlock implements WaterloggedDecoration {
    public static final EnumProperty<Direction> FACING = EnumProperty.create(
            "facing", Direction.class, direction -> direction.getAxis() == Direction.Axis.Y);
    public static final BooleanProperty END = BooleanProperty.create("end");

    public LabyrinthVineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false)
                .setValue(FACING, Direction.DOWN)
                .setValue(END, true));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction direction = clicked.getAxis() == Direction.Axis.Y ? clicked : Direction.DOWN;
        return defaultBlockState().setValue(FACING, direction).setValue(END, true);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTicks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        Direction supportDirection = state.getValue(FACING).getOpposite();
        if (direction == supportDirection && !canSurvive(state, level, pos))
            return state.getFluidState().createLegacyBlock();

        Direction growth = state.getValue(FACING);
        BlockState child = direction == growth ? neighborState
                : level.getBlockState(pos.relative(growth));
        boolean hasChild = child.is(Asterion.LABYRINTH_VINE)
                && child.getValue(FACING) == growth;
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, END);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(END) || random.nextInt(20) != 0) return;

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
        return box(5, 0, 5, 11, 16, 11);
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabyrinthVineBlockEntity(pos, state);
    }
}

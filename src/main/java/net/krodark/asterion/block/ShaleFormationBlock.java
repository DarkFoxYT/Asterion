package net.krodark.asterion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Each block is one straight-sided section of a longer rock formation. */
public final class ShaleFormationBlock extends Block implements WaterloggedDecoration {
    public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 1, 4);
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (int thickness = 1; thickness <= 4; thickness++) {
            double inset = (18 - thickness * 4) / 2.0;
            SHAPES[thickness - 1] = Block.box(inset, 0, inset, 16 - inset, 16, 16 - inset);
        }
    }

    public ShaleFormationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THICKNESS, 1).setValue(HANGING, false)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THICKNESS, HANGING, BlockStateProperties.WATERLOGGED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState support = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
        boolean hanging = face == Direction.DOWN;
        int thickness = 4;
        if (support.getBlock() instanceof ShaleFormationBlock) {
            hanging = support.getValue(HANGING);
            thickness = Math.max(1, support.getValue(THICKNESS) - 1);
        }
        return WaterloggedDecoration.retain(defaultBlockState().setValue(HANGING, hanging)
                .setValue(THICKNESS, thickness), context.getLevel().getFluidState(context.getClickedPos()));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide()) level.setBlock(pos, state.cycle(THICKNESS), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(THICKNESS) - 1];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}

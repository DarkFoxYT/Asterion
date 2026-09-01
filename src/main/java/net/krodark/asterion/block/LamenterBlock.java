package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** A carved face: its four tear tracks drain into the column immediately in front. */
public final class LamenterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRYING = BooleanProperty.create("crying");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public LamenterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(CRYING, false).setValue(ACTIVE, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CRYING, ACTIVE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        return InteractionResult.SUCCESS;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LamenterBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                        BlockEntityType<T> type) {
        return createTickerHelper(type, Asterion.LAMENTER_BLOCK_ENTITY, LamenterBlockEntity::tick);
    }
}

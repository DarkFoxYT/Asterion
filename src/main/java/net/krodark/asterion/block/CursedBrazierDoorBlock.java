package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jspecify.annotations.Nullable;

/** A single rendered anchor backed by a 3x5 interaction and collision plane. */
public final class CursedBrazierDoorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 4);

    public CursedBrazierDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false).setValue(COLUMN, 1).setValue(ROW, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    public static boolean isRoot(BlockState state) { return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0; }
    public static BlockPos root(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), 1 - state.getValue(COLUMN)).below(state.getValue(ROW));
    }
    public static BlockPos part(BlockPos root, Direction facing, int column, int row) {
        return root.relative(facing.getClockWise(), column - 1).above(row);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, COLUMN, ROW);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos root = context.getClickedPos();
        for (int column = 0; column < 3; column++) for (int row = 0; row < 5; row++) {
            BlockPos part = part(root, facing, column, row);
            if (context.getLevel().isOutsideBuildHeight(part)
                    || !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) place(level, pos, state.getValue(FACING));
    }
    public static void place(Level level, BlockPos root, Direction facing) {
        BlockState base = Asterion.CURSED_BRAZIER_DOOR.defaultBlockState().setValue(FACING, facing);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 5; row++)
            level.setBlock(part(root, facing, column, row),
                    base.setValue(COLUMN, column).setValue(ROW, row), UPDATE_CLIENTS);
    }
    public static void setOpen(Level level, BlockPos root, Direction facing, boolean open) {
        for (int column = 0; column < 3; column++) for (int row = 0; row < 5; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.CURSED_BRAZIER_DOOR) && root(pos, state).equals(root))
                level.setBlock(pos, state.setValue(OPEN, open), UPDATE_CLIENTS);
        }
    }
    private InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, ItemStack held) {
        if (!level.isClientSide() && level.getBlockEntity(root(pos, state)) instanceof CursedBrazierDoorBlockEntity door)
            door.toggle(player, held);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, state, player, stack);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        return interact(level, pos, state, player, ItemStack.EMPTY);
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                                BlockPos pos, Direction direction, BlockPos neighbor,
                                                BlockState neighborState, RandomSource random) {
        ticks.scheduleTick(pos, this, 1);
        return state;
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = root(pos, state);
        if (!level.isLoaded(root)) return;
        if (!(level.getBlockEntity(root) instanceof CursedBrazierDoorBlockEntity)) remove(level, root, state.getValue(FACING));
    }
    private static void remove(Level level, BlockPos root, Direction facing) {
        for (int column = 0; column < 3; column++) for (int row = 0; row < 5; row++) {
            BlockPos pos = part(root, facing, column, row);
            if (level.getBlockState(pos).is(Asterion.CURSED_BRAZIER_DOOR)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS);
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (!player.isCreative()) popResource(level, pos, new ItemStack(Asterion.CURSED_BRAZIER_DOOR));
            remove(level, root(pos, state), state.getValue(FACING));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) return isRoot(state) ? box(2, 0, 4, 14, 4, 12) : Shapes.empty();
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? box(0, 0, 3, 16, 16, 13) : box(3, 0, 0, 13, 16, 16);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, level, pos, context);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // BaseEntityBlock marks every part of this multiblock as block-entity capable.
        // Supplying an inert entity for the non-root parts keeps chunk loading valid;
        // only the root is ticked and rendered.
        return new CursedBrazierDoorBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!isRoot(state)) return null;
        return createTickerHelper(type, Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY, CursedBrazierDoorBlockEntity::tick);
    }
}

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

/** One rendered anchor and 34 invisible interaction/collision parts, occupying the authored 7x5 opening. */
public final class MinotaurDoorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 6);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 4);
    public MinotaurDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false).setValue(COLUMN, 3).setValue(ROW, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    public static boolean isRoot(BlockState state) { return state.getValue(COLUMN) == 3 && state.getValue(ROW) == 0; }
    public static BlockPos root(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), 3 - state.getValue(COLUMN)).below(state.getValue(ROW));
    }
    public static BlockPos part(BlockPos root, Direction facing, int column, int row) {
        return root.relative(facing.getClockWise(), column - 3).above(row);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, COLUMN, ROW);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos root = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
            BlockPos pos = part(root, facing, column, row);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        for (int column = 0; column < 7; column++)
            if (!level.getBlockState(part(root, facing, column, 0).below()).isFaceSturdy(
                    level, part(root, facing, column, 0).below(), Direction.UP)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                     @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) place(level, pos, state.getValue(FACING));
    }
    public static void place(Level level, BlockPos root, Direction facing) {
        BlockState base = Asterion.MINOTAUR_DOOR.defaultBlockState().setValue(FACING, facing);
        level.setBlock(root, base, UPDATE_CLIENTS);
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
            if (column == 3 && row == 0) continue;
            level.setBlock(part(root, facing, column, row), base.setValue(COLUMN, column).setValue(ROW, row), UPDATE_CLIENTS);
        }
    }
    public static void setOpen(Level level, BlockPos root, Direction facing, boolean open) {
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.MINOTAUR_DOOR) && root(pos, state).equals(root))
                level.setBlock(pos, state.setValue(OPEN, open), UPDATE_CLIENTS);
        }
    }
    public static void removeDoor(Level level, BlockPos root, Direction facing) {
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.MINOTAUR_DOOR) && root(pos, state).equals(root))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS);
        }
    }
    private InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, ItemStack held) {
        if (level.getBlockEntity(root(pos, state)) instanceof MinotaurDoorBlockEntity door) {
            if (!level.isClientSide()) door.interact(player, held);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, state, player, held);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        return interact(level, pos, state, player, ItemStack.EMPTY);
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        ticks.scheduleTick(pos, this, 1);
        return state;
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = root(pos, state);
        if (!level.isLoaded(root)) return;
        if (!(level.getBlockEntity(root) instanceof MinotaurDoorBlockEntity)) {
            removeDoor(level, root, state.getValue(FACING));
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++)
            if (!level.isLoaded(part(root, facing, column, row))) return;
        for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
            BlockPos part = part(root, facing, column, row);
            BlockState other = level.getBlockState(part);
            if (!other.is(this) || !root(part, other).equals(root)) {
                removeDoor(level, root, facing);
                return;
            }
        }
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(COLUMN, 6 - state.getValue(COLUMN));
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (!player.isCreative()) popResource(level, pos, new ItemStack(Asterion.MINOTAUR_DOOR));
            removeDoor(level, root(pos, state), state.getValue(FACING));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && player.level().dimension().equals(Asterion.ASTERION_LEVEL)
                && root(pos, state).equals(net.krodark.asterion.worldgen.MinotaurArenaEntrances.door(state.getValue(FACING))))
            return 0; // Generated arena gates require their key; ordinary placed doors remain mineable.
        return super.getDestroyProgress(state, player, level, pos);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? box(0, 0, 3, 16, 16, 13) : box(3, 0, 0, 13, 16, 16);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, level, pos, context);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new MinotaurDoorBlockEntity(pos, state) : null;
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, Asterion.MINOTAUR_DOOR_BLOCK_ENTITY, MinotaurDoorBlockEntity::tick);
    }
}

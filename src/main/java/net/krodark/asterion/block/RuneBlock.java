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

/** Nine collision and redstone sections surrounding one rendered 3x3 rune anchor. */
public final class RuneBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    private final int runeIndex;
    public int runeIndex() { return runeIndex; }
    public RuneBlock(int runeIndex, Properties properties) {
        super(properties);
        this.runeIndex = runeIndex;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(COLUMN, 1).setValue(ROW, 0));
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
        builder.add(FACING, POWERED, COLUMN, ROW);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos root = context.getClickedPos();
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos pos = part(root, facing, column, row);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                     @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            place(level, pos, state.getValue(FACING));
            if (level.getBlockEntity(pos) instanceof RuneBlockEntity rune) rune.setWorldGenerated(false);
        }
    }
    public void place(Level level, BlockPos root, Direction facing) {
        BlockState base = defaultBlockState().setValue(FACING, facing);
        level.setBlock(root, base, UPDATE_ALL);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            if (column == 1 && row == 0) continue;
            level.setBlock(part(root, facing, column, row), base.setValue(COLUMN, column).setValue(ROW, row), UPDATE_ALL);
        }
    }
    public static void setPowered(Level level, BlockPos root, Direction facing, boolean powered) {
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RuneBlock && root(pos, state).equals(root))
                level.setBlock(pos, state.setValue(POWERED, powered), UPDATE_CLIENTS);
        }
        // Notify only after ALL nine signal sources have changed. Otherwise the backing
        // conductor can read a still-powered section and keep adjacent circuitry latched.
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction side : Direction.values()) level.updateNeighborsAt(pos.relative(side), state.getBlock());
        }
    }
    public static void removeRune(Level level, BlockPos root, Direction facing) {
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RuneBlock && root(pos, state).equals(root))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS);
        }
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction side : Direction.values()) level.updateNeighborsAt(pos.relative(side), state.getBlock());
        }
    }
    private InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, ItemStack held) {
        if (level.getBlockEntity(root(pos, state)) instanceof RuneBlockEntity rune) {
            if (!level.isClientSide()) rune.interact(player, held);
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
        if (!(level.getBlockEntity(root) instanceof RuneBlockEntity)) {
            removeRune(level, root, state.getValue(FACING));
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++)
            if (!level.isLoaded(part(root, facing, column, row))) return;
        for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
            BlockPos part = part(root, facing, column, row);
            BlockState other = level.getBlockState(part);
            if (!other.is(this) || !root(part, other).equals(root)) {
                removeRune(level, root, facing);
                return;
            }
        }
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(COLUMN, 2 - state.getValue(COLUMN));
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (!player.isCreative()) popResource(level, pos, new ItemStack(this));
            removeRune(level, root(pos, state), state.getValue(FACING));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, 2);
            case EAST -> box(0, 0, 0, 2, 16, 16);
            case WEST -> box(14, 0, 0, 16, 16, 16);
            default -> box(0, 0, 14, 16, 16, 16);
        };
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new RuneBlockEntity(pos, state) : null;
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, Asterion.RUNE_BLOCK_ENTITY, RuneBlockEntity::tick);
    }
}

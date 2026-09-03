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

/** One rendered anchor and 11 invisible interaction/collision parts, occupying the authored 3x4 opening. */
public final class BarrelDoorBlock extends BaseEntityBlock implements WaterloggedDecoration {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty CURSED_LOCKED = BooleanProperty.create("cursed_locked");
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 3);
    public static final BooleanProperty WING = BooleanProperty.create("wing");
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 3);
    public BarrelDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false).setValue(CURSED_LOCKED,false)
                .setValue(WING, false).setValue(COLUMN, 1).setValue(ROW, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    public static boolean isRoot(BlockState state) { return !state.getValue(WING) && state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0; }
    public static BlockPos root(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(WING)
                ? pos.relative(facing.getClockWise(), -1).relative(facing, state.getValue(COLUMN)).below(state.getValue(ROW))
                : pos.relative(facing.getClockWise(), 1 - state.getValue(COLUMN)).below(state.getValue(ROW));
    }
    public static BlockPos part(BlockPos root, Direction facing, int column, int row) {
        return root.relative(facing.getClockWise(), column - 1).above(row);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, CURSED_LOCKED, COLUMN, ROW, WING);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        BlockPos root = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++) {
            BlockPos pos = part(root, facing, column, row);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        for (int column = 0; column < 3; column++)
            if (!level.getBlockState(part(root, facing, column, 0).below()).isFaceSturdy(
                    level, part(root, facing, column, 0).below(), Direction.UP)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                     @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) place(level, pos, state.getValue(FACING));
    }
    public static void place(Level level, BlockPos root, Direction facing) {
        BlockState base = Asterion.BARREL_DOOR.defaultBlockState().setValue(FACING, facing);
        level.setBlock(root, WaterloggedDecoration.retain(base, level.getFluidState(root)), UPDATE_CLIENTS);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++) {
            if (column == 1 && row == 0) continue;
            BlockPos part = part(root, facing, column, row);
            level.setBlock(part, WaterloggedDecoration.retain(base.setValue(COLUMN, column).setValue(ROW, row),
                    level.getFluidState(part)), UPDATE_CLIENTS);
        }
    }
    private static BlockPos wing(BlockPos root, Direction facing, int depth, int row) {
        return root.relative(facing.getClockWise()).relative(facing.getOpposite(), depth).above(row);
    }
    public static boolean prepareSwing(Level level, BlockPos root, Direction facing) {
        for (int depth = 1; depth <= 3; depth++) for (int row = 0; row < 4; row++) {
            BlockPos pos = wing(root, facing, depth, row);
            if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        }
        BlockState base = Asterion.BARREL_DOOR.defaultBlockState().setValue(FACING, facing).setValue(OPEN, true).setValue(WING, true);
        for (int depth = 1; depth <= 3; depth++) for (int row = 0; row < 4; row++) {
            BlockPos part = wing(root, facing, depth, row);
            BlockState existing = level.getBlockState(part);
            // Solid scenery is allowed to clip the visual swing. Keep it intact and omit
            // only that invisible collision cell so the controller can always open.
            if (!existing.isAir()
                    && !(existing.is(Asterion.BARREL_DOOR) && root(part, existing).equals(root))
                    && !(existing.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
                    && !(existing.getBlock() instanceof net.krodark.asterion.fluid.TidalWaterBlock)) continue;
            if (!level.getEntities((net.minecraft.world.entity.Entity)null, new net.minecraft.world.phys.AABB(part),
                    entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) continue;
            level.setBlock(part, WaterloggedDecoration.retain(base.setValue(COLUMN, depth).setValue(ROW, row),
                    level.getFluidState(part)), UPDATE_CLIENTS);
        }
        return true;
    }
    private static void removeWing(Level level, BlockPos root, Direction facing) {
        for (int depth = 1; depth <= 3; depth++) for (int row = 0; row < 4; row++) {
            BlockPos pos = wing(root, facing, depth, row);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.BARREL_DOOR) && state.getValue(WING) && root(pos, state).equals(root))
                level.setBlock(pos, state.getFluidState().createLegacyBlock(), UPDATE_CLIENTS);
        }
    }
    public static void setOpen(Level level, BlockPos root, Direction facing, boolean open) {
        if (!open) removeWing(level, root, facing);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.BARREL_DOOR) && root(pos, state).equals(root))
                level.setBlock(pos, state.setValue(OPEN, open), UPDATE_CLIENTS);
        }
    }
    public static void removeDoor(Level level, BlockPos root, Direction facing) {
        removeWing(level, root, facing);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++) {
            BlockPos pos = part(root, facing, column, row);
            BlockState state = level.getBlockState(pos);
            if (state.is(Asterion.BARREL_DOOR) && root(pos, state).equals(root))
                level.setBlock(pos, state.getFluidState().createLegacyBlock(), UPDATE_CLIENTS);
        }
    }
    private InteractionResult interact(Level level, BlockPos pos, BlockState state, Player player, ItemStack held) {
        if (level.getBlockEntity(root(pos, state)) instanceof BarrelDoorBlockEntity door) {
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
        if (!(level.getBlockEntity(root) instanceof BarrelDoorBlockEntity)) {
            removeDoor(level, root, state.getValue(FACING));
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++)
            if (!level.isLoaded(part(root, facing, column, row))) return;
        // Swing cells are optional collision proxies. prepareSwing deliberately omits
        // them where scenery or an entity occupies the arc, so a missing proxy must
        // never be interpreted as a broken door and delete the visible root model.
        // The original 3x4 plane below remains the authoritative integrity check.
        for (int column = 0; column < 3; column++) for (int row = 0; row < 4; row++) {
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
                .setValue(COLUMN, state.getValue(WING) ? state.getValue(COLUMN) : 2 - state.getValue(COLUMN));
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (!player.isCreative()) popResource(level, pos, new ItemStack(Asterion.BARREL_DOOR));
            removeDoor(level, root(pos, state), state.getValue(FACING));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(OPEN)) return state.getValue(FACING).getAxis() == Direction.Axis.Z
                ? box(0, 0, 5, 16, 16, 11) : box(5, 0, 0, 11, 16, 16);
        if (!state.getValue(WING) && state.getValue(COLUMN) != 2) return Shapes.empty();
        double z0 = state.getValue(WING) ? 0 : 5;
        double z1 = state.getValue(WING) && state.getValue(COLUMN) == 3 ? 5 : 16;
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 16 - z1, 6, 16, 16 - z0);
            case WEST -> box(z0, 0, 0, z1, 16, 6);
            case EAST -> box(16 - z1, 0, 10, 16 - z0, 16, 16);
            default -> box(10, 0, z0, 16, 16, z1);
        };
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Structure NBT may include a block-entity tag for every multipart cell. Accept a
        // lightweight instance for those cells so vanilla does not report failed BE loads;
        // only the root is ticked or rendered.
        return new BarrelDoorBlockEntity(pos, state);
    }
    /** Marks the original 3x4 door plane for the future Cursed Brazier crypt room. */
    public static void setCursedLocked(Level level,BlockPos root,Direction facing,boolean locked) {
        for(int column=0;column<3;column++)for(int row=0;row<4;row++) {
            BlockPos pos=part(root,facing,column,row);
            BlockState state=level.getBlockState(pos);
            if(state.is(Asterion.BARREL_DOOR)&&!state.getValue(WING)&&root(pos,state).equals(root))
                level.setBlock(pos,state.setValue(CURSED_LOCKED,locked),UPDATE_CLIENTS);
        }
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isRoot(state)?createTickerHelper(type,Asterion.BARREL_DOOR_BLOCK_ENTITY,BarrelDoorBlockEntity::tick):null;
    }
}

package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.BlockHitResult;

public final class CrucibleBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 4);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 3);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 4);
    private static final VoxelShape[] COLLISION = makeCollision();
    public CrucibleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 2).setValue(PART_Y, 0).setValue(PART_Z, 2));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new CrucibleBlockEntity(pos, state) : null;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos root = context.getClickedPos();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            BlockPos part = part(root, x, y, z);
            if (context.getLevel().isOutsideBuildHeight(part)
                    || !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) placeStructure(level, pos, state.getValue(FACING));
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos root = root(pos, state);
        BlockState rootState = level.getBlockState(root);
        if (!rootState.is(this) || !isRoot(rootState)) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(root) instanceof CrucibleBlockEntity crucible) {
            if (player.isCrouching()) {
                if (hit.getDirection() == Direction.UP) crucible.removeMold(serverPlayer);
                else crucible.open(serverPlayer);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (crucible.insert(serverPlayer, stack)) return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (!player.isCrouching()) return InteractionResult.PASS;
        BlockPos root = root(pos, state);
        BlockState rootState = level.getBlockState(root);
        if (!rootState.is(this) || !isRoot(rootState)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(root) instanceof CrucibleBlockEntity crucible) {
            if (hit.getDirection() == Direction.UP) crucible.removeMold(serverPlayer);
            else crucible.open(serverPlayer);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        return isRoot(state) ? createTickerHelper(type, Asterion.CRUCIBLE_BLOCK_ENTITY, CrucibleBlockEntity::tick) : null;
    }

    public static boolean isRoot(BlockState state) {
        return state.getValue(PART_X) == 2 && state.getValue(PART_Y) == 0 && state.getValue(PART_Z) == 2;
    }

    public static BlockPos root(BlockPos pos, BlockState state) {
        return pos.offset(2 - state.getValue(PART_X), -state.getValue(PART_Y), 2 - state.getValue(PART_Z));
    }

    private static BlockPos part(BlockPos root, int x, int y, int z) {
        return root.offset(x - 2, y, z - 2);
    }

    private void placeStructure(Level level, BlockPos root, Direction facing) {
        BlockState base = defaultBlockState().setValue(FACING, facing);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++)
            level.setBlock(part(root, x, y, z), base.setValue(PART_X, x).setValue(PART_Y, y)
                    .setValue(PART_Z, z), Block.UPDATE_CLIENTS);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos root = root(pos, state);
            if (!player.getAbilities().instabuild) popResource(level, root, new ItemStack(this));
            removeStructure(level, root);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void removeStructure(Level level, BlockPos root) {
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            BlockPos part = part(root, x, y, z);
            BlockState found = level.getBlockState(part);
            if (found.is(this) && root(part, found).equals(root))
                level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                                BlockPos pos, Direction direction, BlockPos neighborPos,
                                                BlockState neighborState, RandomSource random) {
        ticks.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = root(pos, state);
        BlockState rootState = level.getBlockState(root);
        if (!rootState.is(this) || !isRoot(rootState)) {
            removeStructure(level, root);
            return;
        }
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            BlockPos expected = part(root, x, y, z);
            BlockState found = level.getBlockState(expected);
            if (!found.is(this) || !root(expected, found).equals(root)) {
                removeStructure(level, root);
                return;
            }
        }
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return COLLISION[shapeIndex(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(PART_Z))];
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                     CollisionContext context) { return getShape(state, level, pos, context); }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }

    private static int shapeIndex(int x, int y, int z) { return (y * 5 + z) * 5 + x; }
    private static VoxelShape[] makeCollision() {
        VoxelShape[] shapes = new VoxelShape[100];
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            VoxelShape shape = Shapes.empty();
            // Bedrock model units mapped exactly: a 70x30x70 lower body plus four 12x44 walls.
            shape = addClipped(shape, x, y, z, .3125, .25, .3125, 4.6875, 2.125, 4.6875);
            shape = addClipped(shape, x, y, z, 0, 1.25, 0, 5, 4, .75);
            shape = addClipped(shape, x, y, z, 0, 1.25, 4.25, 5, 4, 5);
            shape = addClipped(shape, x, y, z, 0, 1.25, .75, .75, 4, 4.25);
            shape = addClipped(shape, x, y, z, 4.25, 1.25, .75, 5, 4, 4.25);
            shapes[shapeIndex(x, y, z)] = shape;
        }
        return shapes;
    }

    private static VoxelShape addClipped(VoxelShape shape, int partX, int partY, int partZ,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        double x1 = Math.max(minX, partX), y1 = Math.max(minY, partY), z1 = Math.max(minZ, partZ);
        double x2 = Math.min(maxX, partX + 1), y2 = Math.min(maxY, partY + 1), z2 = Math.min(maxZ, partZ + 1);
        if (x1 >= x2 || y1 >= y2 || z1 >= z2) return shape;
        return Shapes.or(shape, Shapes.box(x1 - partX, y1 - partY, z1 - partZ,
                x2 - partX, y2 - partY, z2 - partZ));
    }
}

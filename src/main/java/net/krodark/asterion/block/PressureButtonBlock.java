package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Surface-mounted hold switch for floors, walls and ceilings. */
public final class PressureButtonBlock extends Block {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;

    public PressureButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(POWERED,false));
    }
    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,POWERED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=defaultBlockState().setValue(FACING,context.getClickedFace());
        return canSurvive(state,context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction facing=state.getValue(FACING);
        Direction support=facing.getOpposite();
        return Block.canSupportCenter(level,pos.relative(support),facing);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,
            BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,
            net.minecraft.util.RandomSource random) {
        return direction==state.getValue(FACING).getOpposite()&&!canSurvive(state,level,pos)
                ?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():state;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,
            Player player,BlockHitResult hit) {
        return InteractionResult.SUCCESS;
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) {
        return state.getValue(POWERED)?15:0;
    }
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) {
        return state.getValue(POWERED)&&side==state.getValue(FACING)?15:0;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return switch(state.getValue(FACING)) {
            case DOWN -> box(2,13,2,14,16,14);
            case NORTH -> box(2,2,13,14,14,16);
            case SOUTH -> box(2,2,0,14,14,3);
            case WEST -> box(13,2,2,16,14,14);
            case EAST -> box(0,2,2,3,14,14);
            default -> box(2,0,2,14,3,14);
        };
    }
}

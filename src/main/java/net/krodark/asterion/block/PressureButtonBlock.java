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

/** Floor-mounted hold switch. The client sends progress only while this block remains targeted. */
public final class PressureButtonBlock extends Block {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;

    public PressureButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,false));
    }
    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,POWERED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state=defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
        return canSurvive(state,context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        return Block.canSupportCenter(level,pos.below(),Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,
            BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,
            net.minecraft.util.RandomSource random) {
        return direction==Direction.DOWN&&!canSurvive(state,level,pos)
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
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return box(4,0,4,12,3,12);
    }
}

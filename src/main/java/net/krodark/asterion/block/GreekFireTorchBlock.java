package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** One renderer supports a wall sconce and a vertically joinable floor-torch column. */
public final class GreekFireTorchBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty RELIGHT = IntegerProperty.create("relight",0,10);
    public final boolean wall;
    public final FireColor fireColor;

    public GreekFireTorchBlock(Properties properties, boolean wall, FireColor fireColor) {
        super(properties);
        this.wall=wall;
        this.fireColor=fireColor;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(TOP,true)
                .setValue(LIT,true).setValue(WATERLOGGED,false).setValue(RELIGHT,0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,TOP,LIT,WATERLOGGED,RELIGHT);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face=context.getClickedFace();
        if(wall) {
            if(!face.getAxis().isHorizontal()) return null;
            BlockPos pos=context.getClickedPos();
            boolean wet=context.getLevel().getFluidState(pos).getType()==net.minecraft.world.level.material.Fluids.WATER;
            BlockState state=defaultBlockState().setValue(FACING,face).setValue(WATERLOGGED,wet).setValue(LIT,!wet);
            return canSurvive(state,context.getLevel(),pos)
                    && context.getLevel().getBlockState(pos.above()).canBeReplaced(context)?state:null;
        }
        if(face!=Direction.UP) return null;
        BlockPos pos=context.getClickedPos();
        boolean wet=context.getLevel().getFluidState(pos).getType()==net.minecraft.world.level.material.Fluids.WATER;
        BlockState state=defaultBlockState().setValue(TOP,
                context.getLevel().getBlockState(pos.above()).getBlock()!=this)
                .setValue(WATERLOGGED,wet).setValue(LIT,!wet);
        return canSurvive(state,context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        if(wall) {
            Direction support=state.getValue(FACING).getOpposite();
            BlockPos supportPos=pos.relative(support);
            return level.getBlockState(supportPos).isFaceSturdy(level,supportPos,state.getValue(FACING));
        }
        BlockPos below=pos.below();
        return level.getBlockState(below).getBlock()==this
                || Block.canSupportCenter(level,below,Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,
            BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,RandomSource random) {
        if(state.getValue(WATERLOGGED)) ticks.scheduleTick(pos,net.minecraft.world.level.material.Fluids.WATER,
                net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        if(!canSurvive(state,level,pos)) return Blocks.AIR.defaultBlockState();
        if(!wall && direction==Direction.UP)
            return state.setValue(TOP,neighbor.getBlock()!=this);
        return state;
    }
    @Override protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)?net.minecraft.world.level.material.Fluids.WATER.getSource(false):super.getFluidState(state);
    }
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,
            net.minecraft.world.level.material.FluidState fluid) {
        if(!SimpleWaterloggedBlock.super.placeLiquid(level,pos,state,fluid)) return false;
        BlockState wet=level.getBlockState(pos);
        if(wet.is(this)) level.setBlock(pos,wet.setValue(LIT,false).setValue(RELIGHT,0),Block.UPDATE_ALL);
        return true;
    }
    @Override public net.minecraft.world.item.ItemStack pickupBlock(net.minecraft.world.entity.LivingEntity entity,
            LevelAccessor level,BlockPos pos,BlockState state) {
        net.minecraft.world.item.ItemStack result=SimpleWaterloggedBlock.super.pickupBlock(entity,level,pos,state);
        if(!result.isEmpty()&&level instanceof net.minecraft.server.level.ServerLevel server) {
            BlockState dry=server.getBlockState(pos);
            if(dry.is(this)) server.setBlock(pos,dry.setValue(LIT,false).setValue(RELIGHT,10),Block.UPDATE_ALL);
            server.scheduleTick(pos,this,20);
        }
        return result;
    }
    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,RandomSource random) {
        if(state.getValue(WATERLOGGED)||state.getValue(LIT)) return;
        int remaining=state.getValue(RELIGHT);
        if(remaining<=1) {
            level.setBlock(pos,state.setValue(LIT,true).setValue(RELIGHT,0),Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos,state.setValue(RELIGHT,remaining-1),Block.UPDATE_CLIENTS);
        level.scheduleTick(pos,this,20);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(!wall) return state.getValue(TOP)?box(1,0,1,15,16,15):box(6,0,6,10,16,10);
        // Native model faces north: its support wall is on the south (+Z) edge.
        // Only the metal cup/shaft collides; the animated flame remains passable.
        VoxelShape north=Shapes.or(
                box(6,1,13,10,9,16),
                box(6,5,10,10,11,14),
                box(6,9,7,10,16,11),
                box(6,14,4,10,20,8),
                box(3,18,1,13,22,10));
        return switch(state.getValue(FACING)) {
            case EAST -> rotateShape(north,1); case SOUTH -> rotateShape(north,2);
            case WEST -> rotateShape(north,3); default -> north;
        };
    }
    private static VoxelShape rotateShape(VoxelShape source,int turns) {
        VoxelShape result=source;
        for(int i=0;i<turns;i++) {
            VoxelShape next=Shapes.empty();
            for(AABB box:result.toAabbs()) next=Shapes.or(next,Shapes.box(1-box.maxZ,box.minY,box.minX,
                    1-box.minZ,box.maxY,box.maxX));
            result=next;
        }
        return result.optimize();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new GreekFireTorchBlockEntity(pos,state);
    }

    public enum FireColor {
        GREEK("torch_greek_fire", .18F, 1.0F, .30F),
        RED("torch_red_fire", 1.0F, .12F, .06F),
        ORANGE("torch_orange_fire", 1.0F, .46F, .08F);
        public final String texture;
        public final float red,green,blue;
        FireColor(String texture,float red,float green,float blue) {
            this.texture=texture; this.red=red; this.green=green; this.blue=blue;
        }
    }
}

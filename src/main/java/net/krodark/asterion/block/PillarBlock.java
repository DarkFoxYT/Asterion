package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.CatacombProtection;
import net.minecraft.core.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import org.jspecify.annotations.Nullable;

/** Authored 3x3 caps and 2x2 shaft, clipped into local collision cells. One root renders the model. */
public final class PillarBlock extends BaseEntityBlock {
    public static final int MODEL_HEIGHT = 27;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column",0,2);
    public static final IntegerProperty ROW = IntegerProperty.create("row",0,26);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth",0,2);
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height",1,27);
    private static final VoxelShape[] SHAPES = shapes();

    public PillarBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(COLUMN,1).setValue(DEPTH,1).setValue(ROW,0).setValue(HEIGHT,MODEL_HEIGHT));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(COLUMN,ROW,DEPTH,HEIGHT); }
    public static boolean isRoot(BlockState state) { return state.getValue(COLUMN)==1 && state.getValue(DEPTH)==1 && state.getValue(ROW)==0; }
    public static BlockPos root(BlockPos pos,BlockState state) { return pos.offset(1-state.getValue(COLUMN),-state.getValue(ROW),1-state.getValue(DEPTH)); }
    public static BlockPos part(BlockPos root,int x,int y,int z) { return root.offset(x-1,y,z-1); }
    private static boolean owned(BlockState state,BlockPos pos,BlockPos root,int height) {
        return state.is(Asterion.PILLAR) && state.getValue(HEIGHT)==height && root(pos,state).equals(root);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level=context.getLevel(); BlockPos root=context.getClickedPos();
        if (!level.getBlockState(root.below()).isFaceSturdy(level,root.below(),Direction.UP)) return null;
        for(int x=0;x<3;x++) for(int y=0;y<MODEL_HEIGHT;y++) for(int z=0;z<3;z++) {
            BlockPos pos=part(root,x,y,z);
            if(!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || context.getPlayer()!=null && CatacombProtection.contains(level,pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
            VoxelShape shape=SHAPES[index(MODEL_HEIGHT,x,y,z)];
            if (!shape.isEmpty() && !level.isUnobstructed(null,shape.move(pos.getX(),pos.getY(),pos.getZ()))) return null;
        }
        return defaultBlockState();
    }
    @Override public void setPlacedBy(Level level,BlockPos root,BlockState state,@Nullable LivingEntity placer,ItemStack stack) {
        if(!level.isClientSide()) placeStructure((pos,part)->level.setBlock(pos,part,UPDATE_CLIENTS),root,state.getValue(HEIGHT));
    }
    public static void placeStructure(java.util.function.BiConsumer<BlockPos,BlockState> place,BlockPos root,int height) {
        if(height<1 || height>MODEL_HEIGHT) throw new IllegalArgumentException("Pillar height must be 1..27");
        for(int x=0;x<3;x++) for(int y=0;y<height;y++) for(int z=0;z<3;z++)
            place.accept(part(root,x,y,z),Asterion.PILLAR.defaultBlockState().setValue(COLUMN,x).setValue(ROW,y).setValue(DEPTH,z).setValue(HEIGHT,height));
    }
    public static void removeStructure(Level level,BlockPos root,int height) {
        for(int x=0;x<3;x++) for(int y=0;y<height;y++) for(int z=0;z<3;z++) {
            BlockPos pos=part(root,x,y,z);
            if(level.isLoaded(pos) && owned(level.getBlockState(pos),pos,root,height)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),UPDATE_ALL);
        }
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide()) {
            if(!player.isCreative()) popResource(level,pos,new ItemStack(this));
            removeStructure(level,root(pos,state),state.getValue(HEIGHT));
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,
            Direction side,BlockPos neighbor,BlockState other,RandomSource random) {
        ticks.scheduleTick(pos,this,1); return state;
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        BlockPos root=root(pos,state); int height=state.getValue(HEIGHT);
        if(!level.isLoaded(root)) { level.scheduleTick(pos,this,20); return; }
        if(!owned(level.getBlockState(root),root,root,height)) { level.setBlock(pos,Blocks.AIR.defaultBlockState(),UPDATE_ALL); return; }
        if(!isRoot(state)) { level.scheduleTick(root,this,1); return; }
        for(int x=0;x<3;x++) for(int y=0;y<height;y++) for(int z=0;z<3;z++)
            if(!level.isLoaded(part(root,x,y,z))) { level.scheduleTick(root,this,20); return; }
        boolean intact=level.getBlockState(root.below()).isFaceSturdy(level,root.below(),Direction.UP);
        for(int x=0;x<3 && intact;x++) for(int y=0;y<height && intact;y++) for(int z=0;z<3;z++)
            if(!owned(level.getBlockState(part(root,x,y,z)),part(root,x,y,z),root,height)) { intact=false; break; }
        if(!intact) { removeStructure(level,root,height); popResource(level,root,new ItemStack(this)); }
    }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return isRoot(state)?new PillarBlockEntity(pos,state):null; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        int x=state.getValue(COLUMN), z=state.getValue(DEPTH);
        return switch(rotation) {
            case CLOCKWISE_90 -> state.setValue(COLUMN,2-z).setValue(DEPTH,x);
            case CLOCKWISE_180 -> state.setValue(COLUMN,2-x).setValue(DEPTH,2-z);
            case COUNTERCLOCKWISE_90 -> state.setValue(COLUMN,z).setValue(DEPTH,2-x);
            default -> state;
        };
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch(mirror) {
            case LEFT_RIGHT -> state.setValue(DEPTH,2-state.getValue(DEPTH));
            case FRONT_BACK -> state.setValue(COLUMN,2-state.getValue(COLUMN));
            default -> state;
        };
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return SHAPES[index(state.getValue(HEIGHT),state.getValue(COLUMN),state.getValue(ROW),state.getValue(DEPTH))];
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return getShape(state,level,pos,context); }
    private static int index(int height,int x,int y,int z) { return (((height-1)*27+y)*3+x)*3+z; }
    private static VoxelShape[] shapes() {
        VoxelShape[] shapes=new VoxelShape[27*27*9];
        for(int h=1;h<=27;h++) for(int y=0;y<27;y++) for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            double scale=h/27.0, ox=(x-1)*16, oy=y*16, oz=(z-1)*16;
            VoxelShape shape=Shapes.empty();
            // Model coordinates translated +8 in X/Z; vertical scaling matches the root bone renderer.
            double[][] cubes={{-16,0,-16,32,16*scale,32},{-8,16*scale,-8,24,416*scale,24},{-16,416*scale,-16,32,432*scale,32}};
            for(double[] cube:cubes) {
                double a=Math.max(0,cube[0]-ox),b=Math.max(0,cube[1]-oy),c=Math.max(0,cube[2]-oz);
                double d=Math.min(16,cube[3]-ox),e=Math.min(16,cube[4]-oy),f=Math.min(16,cube[5]-oz);
                if(a<d && b<e && c<f) shape=Shapes.or(shape,box(a,b,c,d,e,f));
            }
            shapes[index(h,x,y,z)]=shape.optimize();
        }
        return shapes;
    }
}

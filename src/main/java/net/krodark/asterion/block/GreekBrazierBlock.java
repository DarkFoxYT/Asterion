package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jspecify.annotations.Nullable;

/** One visible bowl and eight linked collision parts, all contained in a 3x3 footprint. */
public final class GreekBrazierBlock extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    // Exact unrotated cubes from brazier.geo.json, translated by +8 on X/Z to block space.
    private static final double[][] CUBES = {
            {-11,4,-11,27,13,27}, {-13,13,-13,29,16,29},
            {0,2,0,16,4,16}, {-6,0,-6,22,2,22}
    };
    private static final VoxelShape[] SHAPES = createShapes();

    public GreekBrazierBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, true)
                .setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(COLUMN, 1).setValue(ROW, 1).setValue(FORMED, false));
    }
    public static boolean isRoot(BlockState state) { return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 1; }
    public static BlockPos root(BlockPos pos, BlockState state) { return pos.offset(1-state.getValue(COLUMN), 0, 1-state.getValue(ROW)); }
    private static BlockPos part(BlockPos root, int x, int z) { return root.offset(x-1, 0, z-1); }
    private static boolean owned(BlockState state, BlockPos pos, BlockPos root) {
        return state.getBlock() instanceof GreekBrazierBlock && root(pos, state).equals(root);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT, BlockStateProperties.WATERLOGGED, COLUMN, ROW, FORMED);
    }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        for (int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos pos = part(center,x,z);
            if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        boolean wet = level.getFluidState(center).is(net.minecraft.tags.FluidTags.WATER);
        return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,wet)
                .setValue(BlockStateProperties.LIT,!wet);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server) form(server,pos,state);
    }
    private boolean form(ServerLevel level, BlockPos center, BlockState base) {
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos pos=part(center,x,z);
            BlockState other=level.getBlockState(pos);
            if (!owned(other,pos,center) && !other.canBeReplaced()) return false;
        }
        boolean wet=false;
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos pos=part(center,x,z);
            boolean water=level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
            wet |= water;
            level.setBlock(pos,base.setValue(COLUMN,x).setValue(ROW,z).setValue(FORMED,true)
                    .setValue(BlockStateProperties.WATERLOGGED,water),UPDATE_CLIENTS);
        }
        if(wet) extinguish(level,center);
        return true;
    }
    /** Used by buffered arena generation, which does not run ordinary item-placement callbacks. */
    public static void placeStructure(java.util.function.BiConsumer<BlockPos,BlockState> place, BlockPos center) {
        for(int x=0;x<3;x++) for(int z=0;z<3;z++)
            place.accept(part(center,x,z),Asterion.GREEK_BRAZIER.defaultBlockState()
                    .setValue(COLUMN,x).setValue(ROW,z).setValue(FORMED,true));
    }
    private static void removeAll(Level level, BlockPos center) {
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos pos=part(center,x,z);
            BlockState state=level.getBlockState(pos);
            if(owned(state,pos,center)) level.setBlock(pos,state.getValue(BlockStateProperties.WATERLOGGED)
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),UPDATE_ALL);
        }
    }
    public static boolean extinguish(ServerLevel level, BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof GreekBrazierBlock)) return false;
        BlockPos center=root(pos,state);
        BlockState anchor=level.getBlockState(center);
        if(!owned(anchor,center,center) || !anchor.getValue(BlockStateProperties.LIT)) return false;
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos tile=part(center,x,z);
            BlockState other=level.getBlockState(tile);
            if(owned(other,tile,center)) level.setBlock(tile,other.setValue(BlockStateProperties.LIT,false),UPDATE_ALL);
        }
        level.playSound(null,center,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,1.3F,.8F);
        level.sendParticles(ParticleTypes.SMOKE,center.getX()+.5,center.getY()+1.1,center.getZ()+.5,
                24,.85,.2,.85,.025);
        return true;
    }
    public static boolean relight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GreekBrazierBlock)) return false;
        BlockPos center = root(pos, state);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos tile = part(center, x, z);
            BlockState other = level.getBlockState(tile);
            if (!owned(other, tile, center) || !other.getFluidState().isEmpty()) return false;
        }
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            BlockPos tile = part(center, x, z);
            level.setBlock(tile, level.getBlockState(tile).setValue(BlockStateProperties.LIT, true), UPDATE_ALL);
        }
        level.scheduleTick(center, state.getBlock(), 20);
        level.playSound(null, center, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2F, .7F);
        level.sendParticles(net.krodark.asterion.Asterion.GREEK_FIRE, center.getX()+.5, center.getY()+1.1, center.getZ()+.5, 18, .7, .3, .7, .035);
        return true;
    }
    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);
    }
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid) {
        if(!SimpleWaterloggedBlock.super.placeLiquid(level,pos,state,fluid)) return false;
        if(level instanceof ServerLevel server) extinguish(server,pos);
        return true;
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        if(!level.isClientSide()) level.scheduleTick(pos,this,1);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,
            net.minecraft.world.level.redstone.Orientation orientation,boolean moved) {
        if(!level.isClientSide()) level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        BlockPos center=root(pos,state);
        for(int x=0;x<3;x++) for(int z=0;z<3;z++)
            if(!level.isLoaded(part(center,x,z))) { level.scheduleTick(pos,this,20); return; }
        BlockState anchor=level.getBlockState(center);
        if(!owned(anchor,center,center)) { removeAll(level,center); return; }
        if(!anchor.getValue(FORMED)) {
            if(!form(level,center,anchor)) {
                popResource(level,center,new ItemStack(this)); removeAll(level,center); return;
            }
            anchor=level.getBlockState(center);
        }
        boolean wet=false;
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            BlockPos tile=part(center,x,z);
            BlockState other=level.getBlockState(tile);
            if(!owned(other,tile,center)) {
                popResource(level,center,new ItemStack(this)); removeAll(level,center); return;
            }
            wet |= other.getValue(BlockStateProperties.WATERLOGGED);
            for(Direction direction:Direction.values())
                if(direction!=Direction.DOWN && level.getFluidState(tile.relative(direction)).is(net.minecraft.tags.FluidTags.WATER)) wet=true;
        }
        if(wet) extinguish(level,center);
        if(isRoot(state) && anchor.getValue(BlockStateProperties.LIT)) level.scheduleTick(center,this,20);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide()) {
            if(!player.isCreative()) popResource(level,pos,new ItemStack(this));
            removeAll(level,root(pos,state));
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
            Player player,InteractionHand hand,BlockHitResult hit) {
        if(!state.getValue(BlockStateProperties.LIT)
                || (!stack.is(Items.WATER_BUCKET) && !(stack.getItem() instanceof ShovelItem))) return InteractionResult.PASS;
        if(player.getY()<pos.getY()-2.5D) return InteractionResult.FAIL;
        if(level instanceof ServerLevel server && extinguish(server,pos)
                && stack.is(Items.WATER_BUCKET) && !player.getAbilities().instabuild)
            player.setItemInHand(hand,new ItemStack(Items.BUCKET));
        return InteractionResult.SUCCESS;
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random) {
        if(!isRoot(state) || !state.getValue(BlockStateProperties.LIT)) return;
        for(int i=0;i<3;i++) level.addParticle(Asterion.BRAZIER_FIRE,
                pos.getX()+.5+(random.nextDouble()-.5)*1.6,pos.getY()+1.22+random.nextDouble()*.12,
                pos.getZ()+.5+(random.nextDouble()-.5)*1.6,0,.11,0);
        if(random.nextInt(6)==0) level.addParticle(ParticleTypes.SMOKE,
                pos.getX()+.5,pos.getY()+2.8,pos.getZ()+.5,0,.055,0);
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,
            net.minecraft.world.entity.Entity entity,net.minecraft.world.entity.InsideBlockEffectApplier effects,boolean precise) {
        if (state.getValue(BlockStateProperties.LIT) && entity instanceof LivingEntity
                && level instanceof ServerLevel server) {
            entity.hurtServer(server, server.damageSources().campfire(), 2F);
        }
        super.entityInside(state,level,pos,entity,effects,precise);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return SHAPES[state.getValue(COLUMN)*3+state.getValue(ROW)];
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,
            BlockPos pos,Direction direction,BlockPos neighbor,BlockState other,RandomSource random) {
        ticks.scheduleTick(pos,this,1);
        if(state.getValue(BlockStateProperties.WATERLOGGED)) ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return state;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        int x=state.getValue(COLUMN)-1,z=state.getValue(ROW)-1;
        return switch(rotation) {
            case CLOCKWISE_90 -> state.setValue(COLUMN,1-z).setValue(ROW,1+x);
            case CLOCKWISE_180 -> state.setValue(COLUMN,1-x).setValue(ROW,1-z);
            case COUNTERCLOCKWISE_90 -> state.setValue(COLUMN,1+z).setValue(ROW,1-x);
            default -> state;
        };
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        return switch(mirror) {
            case LEFT_RIGHT -> state.setValue(ROW,2-state.getValue(ROW));
            case FRONT_BACK -> state.setValue(COLUMN,2-state.getValue(COLUMN));
            default -> state;
        };
    }
    private static VoxelShape[] createShapes() {
        VoxelShape[] result=new VoxelShape[9];
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) {
            VoxelShape shape=Shapes.empty();
            double ox=(x-1)*16,oz=(z-1)*16;
            for(double[] cube:CUBES) {
                double minX=Math.max(0,cube[0]-ox),maxX=Math.min(16,cube[3]-ox);
                double minZ=Math.max(0,cube[2]-oz),maxZ=Math.min(16,cube[5]-oz);
                if(minX<maxX && minZ<maxZ) shape=Shapes.or(shape,box(minX,cube[1],minZ,maxX,cube[4],maxZ));
            }
            result[x*3+z]=shape.optimize();
        }
        return result;
    }
}

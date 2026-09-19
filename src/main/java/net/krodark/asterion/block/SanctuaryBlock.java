package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SanctuaryBlock extends BaseEntityBlock {

    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 2);
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    public final boolean altar;
    public SanctuaryBlock(boolean altar, Properties properties) {
        super(properties); this.altar = altar;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0)
                .setValue(PART_X, 1).setValue(PART_Z, 1).setValue(ROW, 0));
    }
    protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, PART_X, PART_Z, ROW);
    }
    public boolean isRoot(BlockState state) {
        return altar || state.getValue(PART_X) == 1 && state.getValue(PART_Z) == 1 && state.getValue(ROW) == 0;
    }
    public BlockPos root(BlockPos pos, BlockState state) {
        return altar ? pos : pos.offset(1-state.getValue(PART_X),-state.getValue(ROW),1-state.getValue(PART_Z));
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        int x = state.getValue(PART_X), z = state.getValue(PART_Z);
        return switch (rotation) {
            case CLOCKWISE_90 -> state.setValue(PART_X, 2 - z).setValue(PART_Z, x);
            case CLOCKWISE_180 -> state.setValue(PART_X, 2 - x).setValue(PART_Z, 2 - z);
            case COUNTERCLOCKWISE_90 -> state.setValue(PART_X, z).setValue(PART_Z, 2 - x);
            default -> state;
        };
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(PART_Z, 2 - state.getValue(PART_Z));
            case FRONT_BACK -> state.setValue(PART_X, 2 - state.getValue(PART_X));
            default -> state;
        };
    }
    private static BlockPos part(BlockPos root,int x,int z,int row) { return root.offset(x-1,row,z-1); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(altar)return defaultBlockState();
        BlockPos root=context.getClickedPos();
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int row=0;row<3;row++) {
            BlockPos part=part(root,x,z,row);
            if(context.getLevel().isOutsideBuildHeight(part)
                    ||!context.getLevel().getBlockState(part).canBeReplaced(context))return null;
        }
        return defaultBlockState();
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,
                                      net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
        if(!altar&&!level.isClientSide())placeObelisk(level,pos,state.getValue(CHARGE));
    }
    public void placeObelisk(Level level,BlockPos root,int charge) {
        BlockState base=defaultBlockState().setValue(CHARGE,charge);
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int row=0;row<3;row++)
            level.setBlock(part(root,x,z,row),base.setValue(PART_X,x).setValue(PART_Z,z).setValue(ROW,row),Block.UPDATE_CLIENTS);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SanctuaryBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if(!isRoot(state))return null;
        return createTickerHelper(type, RespawnObelisks.BLOCK_ENTITY, SanctuaryBlockEntity::tick);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(altar)return box(1,0,1,15,10.5,15);
        int x=state.getValue(PART_X),z=state.getValue(PART_Z);
        double minX=x==0?8:0,maxX=x==2?8:16;
        double minZ=z==0?8:0,maxZ=z==2?8:16;
        return box(minX,0,minZ,maxX,16,maxZ);
    }
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(ItemStack.EMPTY, state, level, pos, player);
    }
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
        return net.krodark.asterion.port.compat.InteractionCompat.item(interact(stack, state, level, pos, player));
    }
    private InteractionResult interact(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if(!altar&&!isRoot(state)) {
            BlockPos root=root(pos,state);BlockState rootState=level.getBlockState(root);
            return rootState.is(this)&&isRoot(rootState)?interact(stack,rootState,level,root,player):InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (altar) {
            if (state.getValue(CHARGE) != 1) {
                serverPlayer.displayClientMessage(Component.translatable(state.getValue(CHARGE) == 0
                        ? "message.asterion.altar_dormant" : "message.asterion.altar_empty"), true);
                return InteractionResult.SUCCESS;
            }
            ItemStack reward = new ItemStack(RespawnObelisks.CHARGED_RUNE);

            if (!player.getInventory().add(reward)) return InteractionResult.FAIL;
            level.setBlock(pos, state.setValue(CHARGE, 2), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1, 1.25F);
            return InteractionResult.SUCCESS;
        }
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            serverPlayer.displayClientMessage(Component.translatable("message.asterion.obelisk_maze_only"), true);
            return InteractionResult.FAIL;
        }
        BlockPos spawn = safeSpawn(server, pos);
        if (spawn == null) {
            serverPlayer.displayClientMessage(Component.translatable("message.asterion.obelisk_blocked"), true);
            return InteractionResult.FAIL;
        }
        if (state.getValue(CHARGE) != 1) {
            level.setBlock(pos, state.setValue(CHARGE, 1), 3);
            if (level.getBlockEntity(pos) instanceof SanctuaryBlockEntity sanctuary) sanctuary.startPulse();
            level.playSound(null, pos, Asterion.RESPAWN_OBELISK_ACTIVATE, SoundSource.BLOCKS, 1.8F, 1.0F);
        } else {
            level.playSound(null, pos, Asterion.RESPAWN_OBELISK_BIND, SoundSource.BLOCKS, 1.8F, 1.0F);
        }
        AsterionWorldState.get(server).setRuneCheckpoint(player.getUUID(), spawn);
        serverPlayer.displayClientMessage(Component.translatable("message.asterion.obelisk_bound"), true);
        return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!altar&&!level.isClientSide()) {
            BlockPos root=root(pos,state);
            if(!player.getAbilities().instabuild)popResource(level,root,new ItemStack(this));
            removeObelisk(level,root);
        }

//? if >=1.20.5 {
return super.playerWillDestroy(level,pos,state,player);
//?} else {
/*super.playerWillDestroy(level,pos,state,player);*/
//?}

    }
    private void removeObelisk(Level level,BlockPos root) {
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int row=0;row<3;row++) {
            BlockPos part=part(root,x,z,row);
            if(level.getBlockState(part).is(this))level.setBlock(part,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
        }
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighborState,
                                               net.minecraft.world.level.LevelAccessor level,
                                               BlockPos pos,BlockPos neighborPos) {
        if(!altar)level.scheduleTick(pos,this,1);
        return state;
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(altar)return;
        BlockPos root=root(pos,state);BlockState rootState=level.getBlockState(root);
        if(!rootState.is(this)||!isRoot(rootState)){removeObelisk(level,root);return;}
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)for(int row=0;row<3;row++) {
            BlockPos part=part(root,x,z,row);BlockState found=level.getBlockState(part);
            if(!found.is(this)||root(part,found).equals(root)==false){removeObelisk(level,root);return;}
        }
    }
    private static BlockPos safeSpawn(ServerLevel level, BlockPos origin) {
        for (int distance = 1; distance <= 3; distance++) for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = origin.relative(direction, distance);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                    && !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)
                    && !level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK)
                    && !level.getBlockState(pos.below()).is(Blocks.CAMPFIRE)
                    && !level.getBlockState(pos.below()).is(Blocks.SOUL_CAMPFIRE)
                    && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) return pos;
        }
        return null;
    }

//? if <1.20.5 {
/*    @Override public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state,
        net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
        net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        var result = useItemOn(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (result != net.krodark.asterion.port.legacy.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return result.result();
        return useWithoutItem(state, level, pos, player, hit);
    }*/
//?}
}

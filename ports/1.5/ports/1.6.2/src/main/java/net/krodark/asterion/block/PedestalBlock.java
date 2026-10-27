package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class PedestalBlock extends BaseEntityBlock {
    public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 4, 13), Block.box(0, 4, 0, 16, 11, 16));
    public PedestalBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(CLAIMED, false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(PedestalBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CLAIMED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PedestalBlockEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator() || state.getValue(CLAIMED)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (!level.setBlock(pos, state.setValue(CLAIMED, true), Block.UPDATE_ALL)) return InteractionResult.FAIL;
            var weapon = new ItemStack(Asterion.AFTERBLOW);
            if (!player.getInventory().add(weapon)) player.drop(weapon, false);
            level.playSound(null, pos, Asterion.AFTERBLOW_PEDESTAL_PULL, net.minecraft.sounds.SoundSource.BLOCKS, 1.3F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }
}

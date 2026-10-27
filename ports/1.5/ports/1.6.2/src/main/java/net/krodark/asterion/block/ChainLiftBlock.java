package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public final class ChainLiftBlock extends BaseEntityBlock {
    public ChainLiftBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(ChainLiftBlock::new); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ChainLiftBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return Block.box(0, 0, 0, 16, 8, 16);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ChainLiftContent.BLOCK_ENTITY, ChainLiftBlockEntity::tick);
    }
}

package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ZiplineAnchorBlock extends BaseEntityBlock {
    public ZiplineAnchorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZiplineAnchorBlockEntity(pos, state);
    }
    // The backing block only persists the two endpoints. The complete anchor/cable is custom-rendered.
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
}

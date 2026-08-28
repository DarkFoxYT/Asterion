package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class ShortGrassBlock extends TallGrassBlock {
    public ShortGrassBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return floor.is(Asterion.MOSSY_ANCIENT_STONE) || floor.is(Blocks.GRASS_BLOCK)
                || floor.is(Blocks.DIRT) || floor.is(Blocks.MOSS_BLOCK)
                || super.mayPlaceOn(floor, level, pos);
    }
}

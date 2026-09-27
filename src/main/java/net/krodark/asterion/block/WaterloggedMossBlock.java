package net.krodark.asterion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class WaterloggedMossBlock extends Block implements WaterloggedDecoration {
    public WaterloggedMossBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
    }
}

package net.krodark.asterion.block;

import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class WaterloggedMossCarpetBlock extends CarpetBlock implements WaterloggedDecoration {
    public WaterloggedMossCarpetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
    }
}

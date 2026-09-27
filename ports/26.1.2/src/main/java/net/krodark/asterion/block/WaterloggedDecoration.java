package net.krodark.asterion.block;

import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.krodark.asterion.fluid.HeavyWaterlogging;

 
public interface WaterloggedDecoration extends SimpleWaterloggedBlock {
    static BlockState retain(BlockState state, FluidState fluid) {
        if (HeavyWaterlogging.isHeavy(fluid.getType())) return HeavyWaterlogging.withFluid(state, fluid);
        return HeavyWaterlogging.dry(state).setValue(BlockStateProperties.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }
}

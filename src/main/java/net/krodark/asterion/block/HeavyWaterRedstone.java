package net.krodark.asterion.block;

import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** Waterlogging for fragile circuitry, without changing its redstone state. */
public interface HeavyWaterRedstone extends SimpleWaterloggedBlock {
    @Override
    default boolean canPlaceLiquid(LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return HeavyWaterlogging.isHeavy(fluid) ? HeavyWaterlogging.amount(state) == 0 : !state.blocksMotion();
    }

    @Override
    default boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        // Structure placement also calls this with the previous EMPTY fluid state.
        // Empty fluid is not water: replacing circuitry with its legacy block deletes it.
        if (fluid.isEmpty()) return false;
        if (HeavyWaterlogging.isHeavy(fluid.getType())) return HeavyWaterlogging.fill(level, pos, state, fluid);
        // Ordinary water buckets retain their original destructive behavior.
        if (!level.isClientSide()) {
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                net.minecraft.world.level.block.Block.dropResources(state, server, pos, level.getBlockEntity(pos));
            level.setBlock(pos, fluid.createLegacyBlock(), 3);
        }
        return true;
    }
}

package net.krodark.asterion.fluid;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;

/** Vanilla water spreading, buckets, swimming and extinguishing, with a separate visual tint. */
public abstract class HeavyWaterFluid extends WaterFluid {
    @Override public Fluid getSource() { return HeavyWater.STILL; }
    @Override public Fluid getFlowing() { return HeavyWater.FLOWING; }
    @Override public Item getBucket() { return HeavyWater.BUCKET; }
    @Override public boolean isSame(Fluid other) {
        return other == HeavyWater.STILL || other == HeavyWater.FLOWING || other == HeavyWater.FLUID;
    }
    @Override public BlockState createLegacyBlock(FluidState state) {
        return HeavyWater.WATER_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }
    public static final class Source extends HeavyWaterFluid {
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }
    public static final class Flowing extends HeavyWaterFluid {
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
}

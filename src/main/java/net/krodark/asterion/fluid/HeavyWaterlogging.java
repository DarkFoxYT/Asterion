package net.krodark.asterion.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/** Stored in normal block palettes: 0 = vanilla/dry, 1–8 = tide, 9 = ordinary Heavy Water. */
public final class HeavyWaterlogging {
    public static final IntegerProperty LEVEL = IntegerProperty.create("heavy_water", 0, 9);
    public static final int NORMAL = 9;
    // Vanilla block caches are constructed before mod fluid registration.
    public static boolean ready;
    private HeavyWaterlogging() { }

    public static boolean isHeavy(Fluid fluid) {
        return fluid instanceof HeavyWaterFluid || fluid instanceof TidalWaterFluid;
    }
    public static boolean supports(BlockState state) { return state.hasProperty(LEVEL); }
    public static int amount(BlockState state) {
        return supports(state) && state.getValue(BlockStateProperties.WATERLOGGED) ? state.getValue(LEVEL) : 0;
    }
    public static boolean isTidal(BlockState state) { int amount = amount(state); return amount > 0 && amount < NORMAL; }
    public static FluidState fluid(int amount) {
        return amount == NORMAL ? HeavyWater.STILL.defaultFluidState() : HeavyWater.FLUID.getFlowing(amount, false);
    }
    public static BlockState withFluid(BlockState state, FluidState fluid) {
        int amount = fluid.getType() instanceof TidalWaterFluid ? fluid.getAmount() : NORMAL;
        return state.setValue(BlockStateProperties.WATERLOGGED, true).setValue(LEVEL, amount);
    }
    public static BlockState dry(BlockState state) {
        return state.setValue(BlockStateProperties.WATERLOGGED, false).setValue(LEVEL, 0);
    }
    public static boolean canFill(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state) {
        return supports(state) && !state.getValue(BlockStateProperties.WATERLOGGED)
                && state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(user, level, pos, state, Fluids.WATER);
    }
    public static boolean fill(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!canFill(null, level, pos, state)) return false;
        var container = (LiquidBlockContainer)state.getBlock();
        // Retain vanilla side effects, e.g. extinguishing a campfire or candle, before retaining the custom fluid.
        if (!container.placeLiquid(level, pos, state, Fluids.WATER.defaultFluidState())) return false;
        if (!level.isClientSide()) {
            BlockState placed = level.getBlockState(pos);
            if (placed.is(state.getBlock()) && supports(placed)) {
                BlockState logged = withFluid(placed, fluid);
                level.setBlock(pos, logged, 3);
                if (amount(logged) == NORMAL) level.scheduleTick(pos, HeavyWater.STILL, HeavyWater.STILL.getTickDelay(level));
            }
        }
        return true;
    }
}

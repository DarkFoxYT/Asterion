package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class HeavyWaterFlowMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void beforeDestroyingBlock(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state);

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void asterion$ordinaryFluid(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state,
            net.minecraft.core.Direction direction, net.minecraft.world.level.material.FluidState fluid,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (state.getBlock() instanceof net.krodark.asterion.block.HeavyWaterRedstone
                && !HeavyWaterlogging.isHeavy(fluid.getType())) {
            beforeDestroyingBlock(level, pos, state);
            level.setBlock(pos, fluid.createLegacyBlock(), 3);
            ci.cancel();
        }
    }
    @Inject(method = "canHoldSpecificFluid", at = @At("HEAD"), cancellable = true)
    private static void asterion$flowIntoWaterloggable(BlockGetter level, BlockPos pos, BlockState state,
                                                     Fluid fluid, CallbackInfoReturnable<Boolean> result) {
        if (HeavyWaterlogging.isHeavy(fluid) && HeavyWaterlogging.supports(state))
            result.setReturnValue(HeavyWaterlogging.canFill(null, level, pos, state));
    }
}

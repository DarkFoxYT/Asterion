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
    @Inject(method = "canHoldSpecificFluid", at = @At("HEAD"), cancellable = true)
    private static void asterion$flowIntoWaterloggable(BlockGetter level, BlockPos pos, BlockState state,
                                                     Fluid fluid, CallbackInfoReturnable<Boolean> result) {
        if (HeavyWaterlogging.isHeavy(fluid) && HeavyWaterlogging.supports(state))
            result.setReturnValue(HeavyWaterlogging.canFill(null, level, pos, state));
    }
}

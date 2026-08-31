package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep shore edges horizontal too; vanilla averages them down toward neighboring air. */
@Mixin(FluidRenderer.class)
public abstract class TidalWaterSurfaceMixin {
    @Inject(method = "calculateAverageHeight", at = @At("HEAD"), cancellable = true)
    private void asterion$flatTide(BlockAndTintGetter level, Fluid fluid, float height,
                                  float first, float second, BlockPos corner, CallbackInfoReturnable<Float> result) {
        if (fluid == HeavyWater.FLUID) result.setReturnValue(height);
    }
}

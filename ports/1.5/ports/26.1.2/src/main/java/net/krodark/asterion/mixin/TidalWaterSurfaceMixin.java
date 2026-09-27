package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.update.underworld.client.LimboWaterRenderer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public abstract class TidalWaterSurfaceMixin {
    @Inject(method = "calculateAverageHeight", at = @At("HEAD"), cancellable = true)
    private void asterion$flatTide(BlockAndTintGetter level, Fluid fluid, float height,
                                  float first, float second, BlockPos corner, CallbackInfoReturnable<Float> result) {
        if (fluid == HeavyWater.FLUID) result.setReturnValue(height);
    }

    // The first neighbor test is the upper face. Suppress only that face; keep native sides,
    // waterfalls and underwater geometry. The replacement is drawn every frame, not baked.
    @Redirect(method = "tesselate", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/FluidRenderer;isNeighborSameFluid(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/FluidState;)Z",
            ordinal = 0))
    private boolean asterion$dynamicSurface(FluidState fluid, FluidState above,
            BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState state, FluidState original) {
        return above.getType().isSame(fluid.getType()) || LimboWaterRenderer.replacesSurface(level, pos);
    }
}

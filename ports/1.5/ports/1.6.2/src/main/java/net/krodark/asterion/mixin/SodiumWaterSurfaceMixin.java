package net.krodark.asterion.mixin;

import net.krodark.asterion.update.underworld.client.LimboWaterRenderer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium builds its own fluid mesh and never calls the vanilla FluidRenderer hook. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumWaterSurfaceMixin {
    @Inject(method = "isFullBlockFluidVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void asterion$replaceOceanTop(BlockAndTintGetter level, BlockPos pos, Direction direction,
                                         BlockState block, FluidState fluid, CallbackInfoReturnable<Boolean> result) {
        if (direction == Direction.UP && LimboWaterRenderer.replacesSurface(level, pos)) result.setReturnValue(false);
    }
}

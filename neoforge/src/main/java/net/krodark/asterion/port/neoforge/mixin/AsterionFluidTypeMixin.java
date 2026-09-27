package net.krodark.asterion.port.neoforge.mixin;

import net.krodark.asterion.fluid.HeavyWaterFluid;
import net.krodark.asterion.fluid.TidalWaterFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge requires every non-vanilla fluid to provide a FluidType. Asterion's
 * two fluids deliberately retain vanilla water behaviour, so expose NeoForge's
 * water type without leaking NeoForge classes into the shared Fabric sources.
 */
@Mixin(Fluid.class)
abstract class AsterionFluidTypeMixin {
    @Inject(method = "getFluidType", at = @At("HEAD"), cancellable = true)
    private void asterion$useWaterFluidType(CallbackInfoReturnable<FluidType> callback) {
        Object fluid = this;
        if (fluid instanceof HeavyWaterFluid || fluid instanceof TidalWaterFluid) {
            callback.setReturnValue(NeoForgeMod.WATER_TYPE.value());
        }
    }
}

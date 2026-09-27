package net.krodark.asterion.forge.mixin;

import net.krodark.asterion.fluid.TidalWaterFluid;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TidalWaterFluid.class)
public abstract class ForgeTidalWaterFluidMixin {
    public FluidType getFluidType() {
        return ForgeMod.WATER_TYPE.get();
    }
}

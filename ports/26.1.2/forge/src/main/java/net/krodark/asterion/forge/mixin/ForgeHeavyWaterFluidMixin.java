package net.krodark.asterion.forge.mixin;

import net.krodark.asterion.fluid.HeavyWaterFluid;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HeavyWaterFluid.class)
public abstract class ForgeHeavyWaterFluidMixin {
    public FluidType getFluidType() {
        return ForgeMod.WATER_TYPE.get();
    }
}

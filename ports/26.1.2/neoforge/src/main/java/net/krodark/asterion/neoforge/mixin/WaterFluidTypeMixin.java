package net.krodark.asterion.neoforge.mixin;

import net.krodark.asterion.fluid.HeavyWaterFluid;
import net.krodark.asterion.fluid.TidalWaterFluid;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;

/** These water variants keep their custom flow/tide logic and vanilla water traits. */
@Mixin({HeavyWaterFluid.class, TidalWaterFluid.class})
public abstract class WaterFluidTypeMixin {
    public FluidType getFluidType() {
        return NeoForgeMod.WATER_TYPE.value();
    }
}

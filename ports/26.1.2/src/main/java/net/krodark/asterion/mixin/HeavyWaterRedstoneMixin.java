package net.krodark.asterion.mixin;

import net.krodark.asterion.block.HeavyWaterRedstone;
import net.minecraft.world.level.block.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({RedStoneWireBlock.class, DiodeBlock.class, RedstoneTorchBlock.class,
        LeverBlock.class, ButtonBlock.class, BasePressurePlateBlock.class,
        TripWireBlock.class, TripWireHookBlock.class, DaylightDetectorBlock.class})
public abstract class HeavyWaterRedstoneMixin implements HeavyWaterRedstone { }

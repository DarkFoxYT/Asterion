package net.krodark.asterion.mixin;

import net.krodark.asterion.block.HeavyWaterRedstone;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Block.class)
public abstract class HeavyWaterRedstoneDefaultsMixin {
    @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
    private BlockState asterion$dryCircuitDefault(BlockState state) {
        return (Object)this instanceof HeavyWaterRedstone ? HeavyWaterlogging.dry(state) : state;
    }
}

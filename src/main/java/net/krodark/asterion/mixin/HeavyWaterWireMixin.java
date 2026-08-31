package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RedStoneWireBlock.class)
public abstract class HeavyWaterWireMixin {
    // Wire rebuilds from its dry cross/dot state when connections or its shape change.
    @ModifyReturnValue(method = "getConnectionState", at = @At("RETURN"))
    private BlockState asterion$retainWater(BlockState result, BlockGetter level, BlockState input, BlockPos pos) {
        BlockState existing = level.getBlockState(pos);
        int amount = HeavyWaterlogging.amount(existing);
        return existing.is(result.getBlock()) && amount > 0
                ? HeavyWaterlogging.withFluid(result, HeavyWaterlogging.fluid(amount)) : result;
    }
}

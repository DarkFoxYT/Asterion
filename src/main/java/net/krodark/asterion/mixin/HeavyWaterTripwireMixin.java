package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TripWireHookBlock.class)
public abstract class HeavyWaterTripwireMixin {
    @WrapOperation(method = "calculateState", at = @At(value = "INVOKE",
            //? if >=26.3 {
            /*target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean asterion$retainEachHooksWater(Level level, BlockPos pos, BlockState next, Operation<Boolean> original) {
    *///?} else {
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean asterion$retainEachHooksWater(Level level, BlockPos pos, BlockState next, int flags, Operation<Boolean> original) {
        //?}
        BlockState old = level.getBlockState(pos);
        int amount = HeavyWaterlogging.amount(old);
        if (old.is(next.getBlock()) && amount > 0)
            next = HeavyWaterlogging.withFluid(next, HeavyWaterlogging.fluid(amount));
        //? if >=26.3 {
        /*return original.call(level, pos, next);
        *///?} else {
        return original.call(level, pos, next, flags);
        //?}
    }
}

package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class HeavyWaterBlockStateMixin {
    @Unique private int asterion$heavyWater;
    @Inject(method = "initCache", at = @At("HEAD"))
    private void asterion$cacheWaterLevel(CallbackInfo info) {
        asterion$heavyWater = HeavyWaterlogging.amount((BlockState)(Object)this);
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void asterion$loggedFluid(CallbackInfoReturnable<FluidState> result) {
        if (asterion$heavyWater > 0 && HeavyWaterlogging.ready)
            result.setReturnValue(HeavyWaterlogging.fluid(asterion$heavyWater));
    }
    @Inject(method = "updateShape", at = @At("RETURN"))
    private void asterion$scheduleHeavyWater(LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                            Direction direction, BlockPos neighborPos, BlockState neighbor,
                                            RandomSource random, CallbackInfoReturnable<BlockState> result) {
        if (asterion$heavyWater == HeavyWaterlogging.NORMAL && HeavyWaterlogging.ready)
            ticks.scheduleTick(pos, HeavyWater.STILL, HeavyWater.STILL.getTickDelay(level));
    }
}

package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class HeavyWaterBlockStateMixin {
    @Unique private int asterion$heavyWater;
    @Unique private boolean asterion$decorationWater;
    @Inject(method = "initCache", at = @At("HEAD"))
    private void asterion$cacheWaterLevel(CallbackInfo info) {
        asterion$heavyWater = HeavyWaterlogging.amount((BlockState)(Object)this);
        var state = (BlockState)(Object)this;
        asterion$decorationWater = state.getBlock() instanceof net.krodark.asterion.block.WaterloggedDecoration
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }
    @ModifyReturnValue(method = "getFluidState", at = @At("RETURN"))
    private FluidState asterion$loggedFluid(FluidState original) {
        if (asterion$heavyWater > 0 && HeavyWaterlogging.ready)
            return HeavyWaterlogging.fluid(asterion$heavyWater);
        if (asterion$decorationWater)
            return net.minecraft.world.level.material.Fluids.WATER.getSource(false);
        return original;
    }
    @Inject(method = "updateShape", at = @At("RETURN"))
    private void asterion$scheduleHeavyWater(Direction direction, BlockState neighbor,
                                            LevelAccessor level, BlockPos pos, BlockPos neighborPos,
                                            CallbackInfoReturnable<BlockState> result) {
        if (asterion$heavyWater == HeavyWaterlogging.NORMAL && HeavyWaterlogging.ready)
            level.scheduleTick(pos, HeavyWater.STILL, HeavyWater.STILL.getTickDelay(level));
        else if (asterion$decorationWater && asterion$heavyWater == 0)
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER,
                    net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
    }
}

package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BucketItem.class)
public abstract class HeavyWaterBucketMixin {
    @Shadow @Final private Fluid content;
    @ModifyExpressionValue(method = {"use", "emptyContents"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
    private net.minecraft.world.level.material.FlowingFluid asterion$allowWaterContainer(net.minecraft.world.level.material.FlowingFluid water) {
        return HeavyWaterlogging.isHeavy(content) ? (net.minecraft.world.level.material.FlowingFluid)content : water;
    }
    @WrapOperation(method = "emptyContents", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/LiquidBlockContainer;canPlaceLiquid(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/Fluid;)Z"))
    private boolean asterion$canWaterlog(LiquidBlockContainer container, LivingEntity user, BlockGetter level,
                                        BlockPos pos, BlockState state, Fluid fluid, Operation<Boolean> original) {
        return HeavyWaterlogging.isHeavy(fluid) ? HeavyWaterlogging.canFill(user, level, pos, state)
                : original.call(container, user, level, pos, state, fluid);
    }
    @WrapOperation(method = "emptyContents", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/LiquidBlockContainer;placeLiquid(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private boolean asterion$placeLoggedWater(LiquidBlockContainer container, LevelAccessor level, BlockPos pos,
                                             BlockState state, FluidState fluid, Operation<Boolean> original) {
        return HeavyWaterlogging.isHeavy(fluid.getType()) ? HeavyWaterlogging.fill(level, pos, state, fluid)
                : original.call(container, level, pos, state, fluid);
    }
}

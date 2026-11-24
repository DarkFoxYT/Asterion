package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleWaterloggedBlock.class)
public interface HeavyWaterPickupMixin {
    @Inject(method = "placeLiquid", at = @At("HEAD"), cancellable = true)
    default void asterion$guardLiquidPlacement(LevelAccessor level, BlockPos pos, BlockState state,
                                               FluidState fluid, CallbackInfoReturnable<Boolean> result) {
        if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) result.setReturnValue(false);
    }

    @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
    //? if >=1.20.5 {
default void asterion$pickUpHeavyWater(Player user, LevelAccessor level, BlockPos pos, BlockState state,
//?} else {
/*default void asterion$pickUpHeavyWater(LevelAccessor level, BlockPos pos, BlockState state,*/
//?}

                                          CallbackInfoReturnable<ItemStack> result) {
        if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            result.setReturnValue(ItemStack.EMPTY);
            return;
        }
        int amount = HeavyWaterlogging.amount(state);
        if (amount == 0) return;
        if (amount < 8) { result.setReturnValue(ItemStack.EMPTY); return; }
        level.setBlock(pos, HeavyWaterlogging.dry(state), 3);
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
        result.setReturnValue(new ItemStack(HeavyWater.BUCKET));
    }
}

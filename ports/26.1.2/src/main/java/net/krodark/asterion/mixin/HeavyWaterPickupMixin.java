package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleWaterloggedBlock.class)
public interface HeavyWaterPickupMixin {
    @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
    private void asterion$pickUpHeavyWater(LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state,
                                          CallbackInfoReturnable<ItemStack> result) {
        int amount = HeavyWaterlogging.amount(state);
        if (amount == 0) return;
        if (amount < 8) { result.setReturnValue(ItemStack.EMPTY); return; }
        level.setBlock(pos, HeavyWaterlogging.dry(state), 3);
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
        result.setReturnValue(new ItemStack(HeavyWater.BUCKET));
    }
}

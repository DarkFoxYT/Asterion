package net.krodark.asterion.mixin.legacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Forge 1.20.1's Mixin runtime cannot inject into interface default methods. */
@Mixin(BucketItem.class)
public abstract class HeavyWaterPickupMixin {
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/BucketPickup;pickupBlock(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack asterion$pickUpHeavyWater(BucketPickup pickup, LevelAccessor level, BlockPos pos,
                                               BlockState state, Operation<ItemStack> original) {
        int amount = HeavyWaterlogging.amount(state);
        if (!(pickup instanceof SimpleWaterloggedBlock) || amount == 0)
            return original.call(pickup, level, pos, state);
        if (amount < 8) return ItemStack.EMPTY;
        level.setBlock(pos, HeavyWaterlogging.dry(state), 3);
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
        return new ItemStack(HeavyWater.BUCKET);
    }
}

package net.krodark.asterion.mixin;

import net.krodark.asterion.worldgen.CatacombProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class CatacombPlayerEditMixin {
    @Inject(method = "mayUseItemAt", at = @At("HEAD"), cancellable = true)
    private void asterion$protectItemEdits(BlockPos pos, Direction face, ItemStack item, CallbackInfoReturnable<Boolean> result) {
        Player player = (Player)(Object)this;
        if (item.getItem() instanceof BucketItem && (CatacombProtection.contains(player.level(), pos)
                || CatacombProtection.contains(player.level(), pos.relative(face.getOpposite()))))
            result.setReturnValue(false);
    }
}

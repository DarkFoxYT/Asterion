package net.krodark.asterion.mixin.legacy;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.krodark.asterion.game.AsterionEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantmentHelper.class)
public abstract class SwiftyWaterDepthMixin {
    @ModifyReturnValue(method = "getDepthStrider", at = @At("RETURN"))
    private static int asterion$swiftyDepth(int original, LivingEntity wearer) {
        return Math.max(original, AsterionEnchantments.swifty(wearer));
    }
}

package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.krodark.asterion.game.AsterionEnchantments;
import net.krodark.asterion.game.EnchantmentBalance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class SwiftyWaterSpeedMixin {
    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float asterion$swiftySpeed(float original) {
        var player = (Player)(Object)this;
        if (player.isSpectator() || player.getAbilities().flying) return original;
        if (!player.isInWater() && !net.krodark.asterion.fluid.HeavyWaterFatigue.touchingHeavyWater(player)) return original;
        return (float)(original * EnchantmentBalance.swimMultiplier(AsterionEnchantments.swifty(player)));
    }
}

package net.krodark.asterion.mixin;

import net.krodark.asterion.fluid.HeavyWaterFatigue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.krodark.asterion.game.AsterionEnchantments;
import net.krodark.asterion.game.EnchantmentBalance;


@Mixin(LivingEntity.class)
public abstract class HeavyWaterMovementMixin {
    @Unique private boolean asterion$heavySwimming;

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 asterion$heavyWaterInput(Vec3 input) {
        asterion$heavySwimming = (Object)this instanceof Player player
                && !player.isSpectator() && !player.getAbilities().flying
                && HeavyWaterFatigue.swimmingInHeavyWater(player);
        if (!asterion$heavySwimming) return input;
        int swifty = AsterionEnchantments.swifty((LivingEntity)(Object)this);
        double horizontal = EnchantmentBalance.heavyPenalty(.65, swifty);
        return input.multiply(horizontal, EnchantmentBalance.heavyPenalty(.75, swifty), horizontal);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void asterion$heavyWaterDrag(Vec3 input, CallbackInfo ci) {
        if (!asterion$heavySwimming) return;
        var entity = (LivingEntity)(Object)this;
        Vec3 motion = entity.getDeltaMovement();

        int swifty = AsterionEnchantments.swifty(entity);
        double horizontal = EnchantmentBalance.heavyPenalty(.88, swifty);
        entity.setDeltaMovement(motion.multiply(horizontal,
                EnchantmentBalance.heavyPenalty(motion.y > 0 ? .78 : .94, swifty), horizontal));
    }

}

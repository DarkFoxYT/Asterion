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

/** Apply identical resistance in client prediction and server-side travel, without teleport corrections. */
@Mixin(LivingEntity.class)
public abstract class HeavyWaterMovementMixin {
    @Unique private boolean asterion$heavySwimming;

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 asterion$heavyWaterInput(Vec3 input) {
        asterion$heavySwimming = (Object)this instanceof Player player
                && !player.isSpectator() && !player.getAbilities().flying
                && HeavyWaterFatigue.swimmingInHeavyWater(player);
        return asterion$heavySwimming ? input.multiply(.65, .75, .65) : input;
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void asterion$heavyWaterDrag(Vec3 input, CallbackInfo ci) {
        if (!asterion$heavySwimming) return;
        var entity = (LivingEntity)(Object)this;
        Vec3 motion = entity.getDeltaMovement();
        // Resist rising more than sinking, but keep upward movement possible; no forced downward pull.
        entity.setDeltaMovement(motion.multiply(.88, motion.y > 0 ? .78 : .94, .88));
    }
}

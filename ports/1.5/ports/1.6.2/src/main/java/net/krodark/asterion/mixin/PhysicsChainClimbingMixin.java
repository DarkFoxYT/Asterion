package net.krodark.asterion.mixin;

import net.krodark.asterion.entity.PhysicsChainEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class PhysicsChainClimbingMixin {
    @Inject(method = "handleOnClimbable", at = @At("RETURN"), cancellable = true)
    private void asterion$chainGrip(net.minecraft.world.phys.Vec3 movement,
                                   CallbackInfoReturnable<net.minecraft.world.phys.Vec3> ci) {
        LivingEntity body = (LivingEntity)(Object)this;
        if (!(body instanceof net.minecraft.world.entity.player.Player player)) return;
        var center=net.krodark.asterion.physics.ChainGrip.center(player);
        if(center==null || body.position().add(0,.9,0).distanceToSqr(center)>16)return;
        body.resetFallDistance();
        if(net.krodark.asterion.physics.ChainGrip.target(player) instanceof PhysicsChainEntity chain && chain.isSpan()) {
            ci.setReturnValue(net.krodark.asterion.physics.ChainClimbingMotion.velocity(body.position(),center,
                    chain.gripTangent(body.position().add(0,.9,0)),body.getYRot(),body.zza,body.isShiftKeyDown()));return;
        }
        ci.setReturnValue(net.krodark.asterion.physics.ChainClimbingMotion.velocity(body.position(),center,
                body.getYRot(),body.zza,body.isShiftKeyDown()));
    }
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void asterion$climbChain(CallbackInfoReturnable<Boolean> ci) {
        if (!ci.getReturnValueZ() && PhysicsChainEntity.canClimb((LivingEntity)(Object)this)) ci.setReturnValue(true);
    }
}

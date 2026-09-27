package net.krodark.asterion.mixin.legacy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class AfterDamageMixin {
 @Unique private final java.util.Deque<Float> asterion$healthBefore = new java.util.ArrayDeque<>();
 @Inject(method="actuallyHurt",at=@At("HEAD")) private void asterion$before(DamageSource source,float amount,CallbackInfo ci) {
  LivingEntity entity=(LivingEntity)(Object)this; asterion$healthBefore.push(entity.getHealth()+entity.getAbsorptionAmount());
 }
 @Inject(method="actuallyHurt",at=@At("RETURN")) private void asterion$after(DamageSource source,float amount,CallbackInfo ci) {
  LivingEntity entity=(LivingEntity)(Object)this;
  float taken=Math.max(0,asterion$healthBefore.pop()-entity.getHealth()-entity.getAbsorptionAmount());
  if(!entity.level().isClientSide() && taken>0) net.krodark.asterion.port.legacy.DamageEvents.AFTER_DAMAGE.invoker().afterDamage(entity,source,amount,taken,false);
 }
}

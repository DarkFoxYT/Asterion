package net.krodark.asterion.port.legacy;
import net.fabricmc.fabric.api.event.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
public final class DamageEvents {
 @FunctionalInterface public interface AfterDamage { void afterDamage(LivingEntity entity,DamageSource source,float baseDamage,float damageTaken,boolean blocked); }
 public static final Event<AfterDamage> AFTER_DAMAGE=EventFactory.createArrayBacked(AfterDamage.class,listeners->(e,s,b,d,k)->{for(var listener:listeners)listener.afterDamage(e,s,b,d,k);});
}

package net.krodark.asterion.port.compat;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
public final class EntityCompat {
 public static double reach(Player player){
 //? if >=1.20.5 {
 return player.entityInteractionRange();
 //?} else {
 /*return player.isCreative()?5.0:3.0;*/
 //?}
 }
 public static void ignite(Entity entity,float seconds){
 //? if >=1.20.5 {
 entity.igniteForSeconds(seconds);
 //?} else {
 /*entity.setSecondsOnFire((int)Math.ceil(seconds));*/
 //?}
 }
 public static void igniteTicks(Entity entity,int ticks){entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(),ticks));}
 public static void hurtAndBreak(ItemStack stack,int amount,LivingEntity entity,EquipmentSlot slot){
 //? if >=1.20.5 {
 stack.hurtAndBreak(amount,entity,slot);
 //?} else {
 /*stack.hurtAndBreak(amount,entity,e->e.broadcastBreakEvent(slot));*/
 //?}
 }
 public static void broken(LivingEntity entity,Item item,EquipmentSlot slot){
 //? if >=1.20.5 {
 entity.onEquippedItemBroken(item,slot);
 //?} else {
 /*entity.broadcastBreakEvent(slot);*/
 //?}
 }
}

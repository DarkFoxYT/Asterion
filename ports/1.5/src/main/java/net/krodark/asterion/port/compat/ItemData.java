package net.krodark.asterion.port.compat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;
/** Version-neutral access to persisted item data. */
public final class ItemData {
 private ItemData() {}
 public static <T> void remove(ItemStack stack,DataComponentType<T> type){
 //? if >=1.20.5 {
 stack.remove(type);
 //?} else {
 /*if(type == net.krodark.asterion.port.legacy.item.DataComponents.CUSTOM_DATA) stack.removeTagKey("asterion:data");
 else if(type == net.krodark.asterion.port.legacy.item.DataComponents.LORE) {
  var display = stack.getTagElement("display"); if(display != null) display.remove("Lore");
 } else throw new IllegalArgumentException("Unsupported legacy component removal");*/
 //?}
 }

 public static <T> T get(ItemStack stack,DataComponentType<T> type){
 //? if >=1.20.5 {
 return stack.get(type);
 //?} else {
 /*return type.read().apply(stack);*/
 //?}
 }
 public static <T> void set(ItemStack stack,DataComponentType<T> type,T value){
 //? if >=1.20.5 {
 stack.set(type,value);
 //?} else {
 /*type.write().accept(stack,value);*/
 //?}
 }
}

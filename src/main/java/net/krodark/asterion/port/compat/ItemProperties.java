package net.krodark.asterion.port.compat;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.ItemAttributeModifiers;
public final class ItemProperties extends Item.Properties {
 //? if <1.20.5 {
 /*public final java.util.Map<DataComponentType<?>,Object> defaults=new java.util.HashMap<>();*/
 //?}
 public <T> ItemProperties component(DataComponentType<T> type,T value){
 //? if >=1.20.5 {
 super.component(type,value);
 //?} else {
 /*defaults.put(type,value);*/
 //?}
 return this;
 }
 public ItemProperties attributes(ItemAttributeModifiers attributes){
 //? if >=1.20.5 {
 super.attributes(attributes);
 //?} else {
 /*defaults.put(net.krodark.asterion.port.legacy.item.DataComponents.ATTRIBUTE_MODIFIERS,attributes);*/
 //?}
 return this;
 }
 @Override public ItemProperties durability(int value){super.durability(value);return this;}
 @Override public ItemProperties stacksTo(int value){super.stacksTo(value);return this;}
 @Override public ItemProperties rarity(Rarity value){super.rarity(value);return this;}
 @Override public ItemProperties fireResistant(){super.fireResistant();return this;}
}

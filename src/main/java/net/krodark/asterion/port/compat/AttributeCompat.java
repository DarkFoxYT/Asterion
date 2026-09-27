package net.krodark.asterion.port.compat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
public final class AttributeCompat {
 public static final ResourceLocation ATTACK_DAMAGE=ResourceLocation.fromNamespaceAndPath("minecraft","base_attack_damage");
 public static final ResourceLocation ATTACK_SPEED=ResourceLocation.fromNamespaceAndPath("minecraft","base_attack_speed");
 private static java.util.UUID uuid(ResourceLocation id){if(id.equals(ATTACK_DAMAGE))return java.util.UUID.fromString("cb3f55d3-645c-4f38-a497-9c13a33db5cf");if(id.equals(ATTACK_SPEED))return java.util.UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca3");return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public static AttributeModifier of(ResourceLocation id,double amount,AttributeModifier.Operation operation){
 //? if >=1.20.5 {
 return new AttributeModifier(id,amount,operation);
 //?} else {
 /*return new AttributeModifier(uuid(id),id.toString(),amount,operation);*/
 //?}
 }
 public static void remove(AttributeInstance attribute,ResourceLocation id){
 //? if >=1.20.5 {
 attribute.removeModifier(id);
 //?} else {
 /*attribute.removeModifier(uuid(id));*/
 //?}
 }
}

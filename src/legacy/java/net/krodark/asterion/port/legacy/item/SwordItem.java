package net.krodark.asterion.port.legacy.item;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.krodark.asterion.port.compat.ItemProperties;
public class SwordItem extends net.minecraft.world.item.SwordItem {
 public SwordItem(Tier tier,Item.Properties properties){super(tier, (int)(amount(properties,Attributes.ATTACK_DAMAGE,3+tier.getAttackDamageBonus())-tier.getAttackDamageBonus()),(float)amount(properties,Attributes.ATTACK_SPEED,-2.4),properties);}
 private static double amount(Item.Properties p,Attribute a,double fallback){if(p instanceof ItemProperties props && props.defaults.get(DataComponents.ATTRIBUTE_MODIFIERS) instanceof ItemAttributeModifiers attrs)for(var e:attrs.entries())if(e.attribute()==a)return e.modifier().getAmount();return fallback;}
 public static ItemAttributeModifiers createAttributes(Tier tier,int damage,float speed){return ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Weapon modifier",damage+tier.getAttackDamageBonus(),AttributeModifier.Operation.ADDITION),EquipmentSlot.MAINHAND).add(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Weapon modifier",speed,AttributeModifier.Operation.ADDITION),EquipmentSlot.MAINHAND).build();}
}

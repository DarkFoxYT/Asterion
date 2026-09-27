package net.krodark.asterion.port.legacy.item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import java.util.*;
public final class DataComponents {
 public static final DataComponentType<CustomData> CUSTOM_DATA=new DataComponentType<>(s->s.getTag()!=null && s.getTag().contains("asterion:data",10)?CustomData.of(s.getTag().getCompound("asterion:data")):null,(s,v)->s.getOrCreateTag().put("asterion:data",v.copyTag()));
 public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA=new DataComponentType<>(s->s.hasTag()&&s.getTag().contains("CustomModelData")?new CustomModelData(s.getTag().getInt("CustomModelData")):null,(s,v)->s.getOrCreateTag().putInt("CustomModelData",v.value()));
 public static final DataComponentType<Component> CUSTOM_NAME=new DataComponentType<>(s->s.hasCustomHoverName()?s.getHoverName():null,ItemStack::setHoverName);
 public static final DataComponentType<Integer> DAMAGE=new DataComponentType<>(ItemStack::getDamageValue,ItemStack::setDamageValue);
 public static final DataComponentType<Integer> MAX_DAMAGE=new DataComponentType<>(s->s.hasTag()&&s.getTag().contains("asterion:max_damage")?s.getTag().getInt("asterion:max_damage"):s.getMaxDamage(),(s,v)->s.getOrCreateTag().putInt("asterion:max_damage",v));
 public static final DataComponentType<ItemLore> LORE=new DataComponentType<>(s->{if(!s.hasTag())return null;var list=s.getTag().getCompound("display").getList("Lore",8);var lines=new ArrayList<Component>();for(Tag t:list){var c=Component.Serializer.fromJson(t.getAsString());if(c!=null)lines.add(c);}return new ItemLore(lines);},(s,v)->{var lines=new ListTag();for(var c:v.lines())lines.add(StringTag.valueOf(Component.Serializer.toJson(c)));s.getOrCreateTagElement("display").put("Lore",lines);});
 public static final DataComponentType<LodestoneTracker> LODESTONE_TRACKER=new DataComponentType<>(s->{var t=s.getTag();if(t==null||!t.contains("LodestonePos"))return null;var dim=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(t.getString("LodestoneDimension")));return new LodestoneTracker(Optional.of(GlobalPos.of(dim,NbtUtils.readBlockPos(t.getCompound("LodestonePos")))),t.getBoolean("LodestoneTracked"));},(s,v)->{var t=s.getOrCreateTag();t.putBoolean("LodestoneTracked",v.tracked());if(v.target().isPresent()){var p=v.target().get();t.put("LodestonePos",NbtUtils.writeBlockPos(p.pos()));t.putString("LodestoneDimension",p.dimension().location().toString());}else{t.remove("LodestonePos");t.remove("LodestoneDimension");}});
 public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS=new DataComponentType<>(s->ItemAttributeModifiers.EMPTY,(s,v)->{s.getOrCreateTag().remove("AttributeModifiers");for(var e:v.entries())s.addAttributeModifier(e.attribute(),e.modifier(),e.slot());});
}

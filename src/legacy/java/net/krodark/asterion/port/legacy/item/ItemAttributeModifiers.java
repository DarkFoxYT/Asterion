package net.krodark.asterion.port.legacy.item;
import java.util.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
public record ItemAttributeModifiers(List<Entry> entries) {
 public record Entry(Attribute attribute,AttributeModifier modifier,EquipmentSlot slot) {}
 public static final ItemAttributeModifiers EMPTY=new ItemAttributeModifiers(List.of());
 public static Builder builder(){return new Builder();}
 public static class Builder {private final List<Entry> entries=new ArrayList<>(); public Builder add(Attribute a,AttributeModifier m,EquipmentSlot s){entries.add(new Entry(a,m,s));return this;}public ItemAttributeModifiers build(){return new ItemAttributeModifiers(List.copyOf(entries));}}
}

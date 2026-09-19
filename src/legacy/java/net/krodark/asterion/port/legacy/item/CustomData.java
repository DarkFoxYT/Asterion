package net.krodark.asterion.port.legacy.item;
import net.minecraft.nbt.CompoundTag;
public record CustomData(CompoundTag tag) { public static CustomData of(CompoundTag tag){return new CustomData(tag.copy());} public boolean isEmpty(){return tag.isEmpty();} public CompoundTag copyTag(){return tag.copy();} }

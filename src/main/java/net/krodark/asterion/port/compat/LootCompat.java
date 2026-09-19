package net.krodark.asterion.port.compat;
import net.minecraft.resources.*;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
public final class LootCompat {
 public static final ResourceKey<net.minecraft.core.Registry<LootTable>> REGISTRY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", "loot_table"));
 public static ResourceLocation get(RandomizableContainerBlockEntity container){
 //? if >=1.20.5 {
 return container.getLootTable()==null?null:container.getLootTable().location();
 //?} else {
 /*return ((net.krodark.asterion.mixin.legacy.LootTableAccessor)container).asterion$loot();*/
 //?}
 }
 public static void set(RandomizableContainerBlockEntity container,ResourceKey<LootTable> key){
 //? if >=1.20.5 {
 container.setLootTable(key);
 //?} else {
 /*container.setLootTable(key.location(),0);*/
 //?}
 }
 public static void seed(RandomizableContainerBlockEntity container,long seed){
 //? if >=1.20.5 {
 container.setLootTableSeed(seed);
 //?} else {
 /*container.setLootTable(get(container),seed);*/
 //?}
 }
}

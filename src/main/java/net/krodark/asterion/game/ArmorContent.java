package net.krodark.asterion.game;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import java.util.List;
import java.util.Map;

public final class ArmorContent {
    public record Set(String name, ArmorMaterial material, List<Item> pieces) {}
    public static final List<Set> SETS = List.of(
            register("tarnished_gold", 22, 2, 6, 5, 2, 22, 0, 0),
            register("celestial_bronze", 28, 3, 7, 6, 3, 18, 1, 0),
            register("celestial_gold", 30, 3, 7, 6, 3, 25, 1, 0),
            register("celestial_steel", 36, 3, 8, 6, 3, 16, 2, .05F));
    private ArmorContent() {}

    private static Set register(String name, int durability, int head, int chest, int legs, int feet,
                                int enchantability, float toughness, float knockbackResistance) {
        var material = new ArmorMaterial(durability,
                Map.of(ArmorType.HELMET, head, ArmorType.CHESTPLATE, chest,
                        ArmorType.LEGGINGS, legs, ArmorType.BOOTS, feet, ArmorType.BODY, chest),
                enchantability, SoundEvents.ARMOR_EQUIP_IRON, toughness, knockbackResistance,
                TagKey.create(Registries.ITEM, Asterion.id("repairs_" + name + "_armor")),
                ResourceKey.create(EquipmentAssets.ROOT_ID, Asterion.id(name)));
        return new Set(name, material, List.of(piece(name, "helmet", material, ArmorType.HELMET),
                piece(name, "chestplate", material, ArmorType.CHESTPLATE),
                piece(name, "leggings", material, ArmorType.LEGGINGS),
                piece(name, "boots", material, ArmorType.BOOTS)));
    }
    private static Item piece(String materialName, String slot, ArmorMaterial material, ArmorType type) {
        var key = ResourceKey.create(Registries.ITEM, Asterion.id(materialName + "_" + slot));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
    }
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("forging")))
                .register(output -> SETS.forEach(set -> set.pieces().forEach(output::accept)));
    }
}

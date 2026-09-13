package net.krodark.asterion.game;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.Map;

public final class ArmorContent {
    public record Set(String name, Holder<ArmorMaterial> material, List<Item> pieces) {}
    public static final List<Set> SETS = List.of(
            register("tarnished_gold", 22, 2, 6, 5, 2, 22, 0, 0),
            register("celestial_bronze", 28, 3, 7, 6, 3, 18, 1, 0),
            register("celestial_gold", 30, 3, 7, 6, 3, 25, 1, 0),
            register("celestial_steel", 36, 3, 8, 6, 3, 16, 2, .05F));
    private ArmorContent() {}

    private static Set register(String name, int durability, int head, int chest, int legs, int feet,
                                int enchantability, float toughness, float knockbackResistance) {
        TagKey<Item> repairs = TagKey.create(Registries.ITEM, Asterion.id("repairs_" + name + "_armor"));
        var material = Holder.direct(new ArmorMaterial(
                Map.of(ArmorItem.Type.HELMET, head, ArmorItem.Type.CHESTPLATE, chest,
                        ArmorItem.Type.LEGGINGS, legs, ArmorItem.Type.BOOTS, feet, ArmorItem.Type.BODY, chest),
                enchantability, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(repairs),
                List.of(new ArmorMaterial.Layer(Asterion.id(name))), toughness, knockbackResistance));
        return new Set(name, material, List.of(piece(name, "helmet", material, ArmorItem.Type.HELMET, durability),
                piece(name, "chestplate", material, ArmorItem.Type.CHESTPLATE, durability),
                piece(name, "leggings", material, ArmorItem.Type.LEGGINGS, durability),
                piece(name, "boots", material, ArmorItem.Type.BOOTS, durability)));
    }
    private static Item piece(String materialName, String slot, Holder<ArmorMaterial> material,
                              ArmorItem.Type type, int durability) {
        var key = ResourceKey.create(Registries.ITEM, Asterion.id(materialName + "_" + slot));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new ArmorItem(material, type, new Item.Properties().durability(type.getDurability(durability))));
    }
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("forging")))
                .register(output -> SETS.forEach(set -> set.pieces().forEach(output::accept)));
    }
}

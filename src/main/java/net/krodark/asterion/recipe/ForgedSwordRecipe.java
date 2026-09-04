package net.krodark.asterion.recipe;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

/** Assembles three genuinely forged sword components. */
public final class ForgedSwordRecipe extends CustomRecipe {
    @Override public boolean matches(CraftingInput input, Level level) {
        return parts(input) != null;
    }

    @Override public ItemStack assemble(CraftingInput input) {
        ItemStack[] parts = parts(input);
        if (parts == null) return ItemStack.EMPTY;
        CompoundTag blade = data(parts[0]), guard = data(parts[1]), pommel = data(parts[2]);
        int edge = value(blade, "edge", 7);
        int hardness = Math.round((value(blade, "hardness", 7) * 2
                + value(guard, "hardness", 7) + value(pommel, "hardness", 7)) / 4F);
        int weight = Math.round((value(blade, "weight", 8) * 2
                + value(guard, "weight", 8) + value(pommel, "weight", 8)) / 4F);
        int purity = Math.round((value(blade, "purity", 75) * 2
                + value(guard, "purity", 75) + value(pommel, "purity", 75)) / 4F);
        double damage = Math.clamp(2.5D + edge * .62D + purity * .012D, 4D, 14D);
        double attackSpeed = Math.clamp(2.05D - weight * .075D, 1.05D, 1.75D);
        int durability = Math.clamp(100 + hardness * 42 + purity * 3, 250, 1400);

        ItemStack result = new ItemStack(Asterion.FORGED_SWORD);
        result.set(DataComponents.MAX_DAMAGE, durability);
        result.set(DataComponents.DAMAGE, 0);
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        damage - 1D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        attackSpeed - 4D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        String bladeMaterial = primaryMaterial(blade), guardMaterial = primaryMaterial(guard);
        String pommelMaterial = primaryMaterial(pommel);
        java.util.ArrayList<String> renderMaterials = new java.util.ArrayList<>(12);
        java.util.ArrayList<Integer> renderColors = new java.util.ArrayList<>(12);
        appendLayers(blade, renderMaterials, renderColors);
        appendLayers(guard, renderMaterials, renderColors);
        appendLayers(pommel, renderMaterials, renderColors);
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(),
                renderMaterials, renderColors));
        boolean uniform = bladeMaterial.equals(guardMaterial) && bladeMaterial.equals(pommelMaterial);
        String title = uniform ? displayName(bladeMaterial) + " Sword" : "Custom Forged Sword";
        result.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(ChatFormatting.WHITE));
        result.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Blade: " + displayName(bladeMaterial)).withStyle(ChatFormatting.GRAY),
                Component.literal("Guard: " + displayName(guardMaterial)).withStyle(ChatFormatting.GRAY),
                Component.literal("Pommel: " + displayName(pommelMaterial)).withStyle(ChatFormatting.GRAY),
                Component.literal("Purity " + purity + "%  Weight " + weight).withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(String.format(java.util.Locale.ROOT, "Damage %.1f  Speed %.2f", damage, attackSpeed))
                        .withStyle(ChatFormatting.DARK_GRAY))));
        CompoundTag forged = new CompoundTag();
        forged.putString("blade_material", bladeMaterial); forged.putString("guard_material", guardMaterial);
        forged.putString("pommel_material", pommelMaterial);
        forged.putString("metal_sequence", blade.getStringOr("metal_sequence", ""));
        forged.putInt("purity", purity);
        forged.putInt("edge", edge); forged.putInt("hardness", hardness); forged.putInt("weight", weight);
        forged.putDouble("attack_damage", damage); forged.putDouble("attack_speed", attackSpeed);
        forged.putInt("durability", durability);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(forged));
        return result;
    }

    private static ItemStack[] parts(CraftingInput input) {
        ItemStack blade = ItemStack.EMPTY, guard = ItemStack.EMPTY, pommel = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) if (!stack.isEmpty()) {
            if (stack.is(Asterion.FORGED_SWORD_BLADE) && blade.isEmpty()) blade = stack;
            else if (stack.is(Asterion.FORGED_SWORD_GUARD) && guard.isEmpty()) guard = stack;
            else if (stack.is(Asterion.FORGED_SWORD_POMMEL) && pommel.isEmpty()) pommel = stack;
            else return null;
        }
        return blade.isEmpty() || guard.isEmpty() || pommel.isEmpty() ? null : new ItemStack[]{blade, guard, pommel};
    }
    private static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
    private static int value(CompoundTag tag, String key, int fallback) { return tag.getIntOr(key, fallback); }
    private static String primaryMaterial(CompoundTag tag) {
        String sequence = tag.getStringOr("metal_sequence", "");
        return sequence.isEmpty() ? "iron" : CrucibleBlockEntity.metalId(sequence.charAt(0) - '0');
    }
    private static void appendLayers(CompoundTag tag, java.util.List<String> materials,
                                     java.util.List<Integer> colors) {
        String sequence = tag.getStringOr("metal_sequence", "");
        for (int layer = 0; layer < 4; layer++) {
            materials.add(layer < sequence.length()
                    ? CrucibleBlockEntity.metalId(sequence.charAt(layer) - '0') : "none");
            colors.add(layer >= sequence.length() ? 0x00FFFFFF
                    : layer == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
    }
    private static String displayName(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) name.append(name.isEmpty() ? "" : " ")
                .append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return name.toString();
    }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return Asterion.FORGED_SWORD_RECIPE; }
}

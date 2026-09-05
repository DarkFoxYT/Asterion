package net.krodark.asterion.compat;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** Viewer-neutral descriptions of the Crucible's dynamic forging rules. */
public record CrucibleViewerRecipe(Identifier id, Item mold, int temperature, List<ItemStack> outputs) {
    private static final List<ItemStack> METALS = List.of(
            new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.COPPER_INGOT),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Asterion.TARNISHED_GOLD_INGOT),
            new ItemStack(Items.NETHERITE_INGOT),
            new ItemStack(Asterion.CELESTIAL_BRONZE_INGOT),
            new ItemStack(Asterion.BONESTEEL_INGOT),
            new ItemStack(Asterion.CELESTIAL_STEEL_INGOT),
            new ItemStack(Asterion.CELESTIAL_GOLD_INGOT),
            remeltableIngot());

    public static List<ItemStack> metals() {
        return METALS.stream().map(ItemStack::copy).toList();
    }

    public static List<CrucibleViewerRecipe> all() {
        return List.of(
                recipe("ingot", CrucibleBlockEntity.Mold.INGOT, Asterion.INGOT_CAST,
                        Asterion.FORGED_INGOT, Asterion.TARNISHED_GOLD_INGOT),
                recipe("sword_guard", CrucibleBlockEntity.Mold.SWORD_GUARD,
                        Asterion.SWORD_GUARD_CAST, Asterion.FORGED_SWORD_GUARD),
                recipe("sword_pommel", CrucibleBlockEntity.Mold.SWORD_POMMEL,
                        Asterion.SWORD_POMMEL_CAST, Asterion.FORGED_SWORD_POMMEL),
                recipe("sword_blade", CrucibleBlockEntity.Mold.SWORD_BLADE,
                        Asterion.SWORD_BLADE_CAST, Asterion.FORGED_SWORD_BLADE),
                recipe("axe_head", CrucibleBlockEntity.Mold.AXE_HEAD,
                        Asterion.AXE_HEAD_CAST, Asterion.FORGED_AXE_HEAD),
                recipe("minotaur_key", CrucibleBlockEntity.Mold.MINOTAUR_KEY,
                        Asterion.MINOTAUR_KEY_CAST, Asterion.MINOTAUR_KEY));
    }

    private static CrucibleViewerRecipe recipe(String name, CrucibleBlockEntity.Mold mold,
                                                Item cast, Item... outputs) {
        return new CrucibleViewerRecipe(Asterion.id("crucible/" + name), cast, mold.target(),
                java.util.Arrays.stream(outputs).map(ItemStack::new).toList());
    }

    private static ItemStack remeltableIngot() {
        ItemStack stack = new ItemStack(Asterion.FORGED_INGOT);
        net.minecraft.nbt.CompoundTag alloy = new net.minecraft.nbt.CompoundTag();
        alloy.putString("metal_sequence", "0");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(alloy));
        return stack;
    }
}

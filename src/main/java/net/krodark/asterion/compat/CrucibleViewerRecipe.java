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

 
public record CrucibleViewerRecipe(Identifier id, List<List<ItemStack>> inputs, Item mold,
                                   int temperature, List<ItemStack> outputs, String instructionKey) {
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

    public static List<ItemStack> heatSources() {
        return List.of(new ItemStack(Items.CAMPFIRE), new ItemStack(Items.SOUL_CAMPFIRE),
                new ItemStack(Items.MAGMA_BLOCK), new ItemStack(Items.LAVA_BUCKET));
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
                new CrucibleViewerRecipe(Asterion.id("crucible/minotaur_key"),
                        List.of(List.of(new ItemStack(Asterion.BONESTEEL_INGOT, 3)),
                                List.of(new ItemStack(net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER_KEY))),
                        Asterion.MINOTAUR_KEY_CAST, CrucibleBlockEntity.Mold.MINOTAUR_KEY.target(),
                        List.of(new ItemStack(Asterion.MINOTAUR_KEY)),
                        "recipe.asterion.crucible.minotaur_key"),
                reaction("celestial_bronze", List.of(List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Asterion.TARNISHED_GOLD_INGOT)),
                                List.of(new ItemStack(Items.COPPER_INGOT))),
                        new ItemStack(Asterion.CELESTIAL_BRONZE_INGOT), "recipe.asterion.crucible.bronze"),
                reaction("celestial_steel", List.of(List.of(new ItemStack(Items.IRON_INGOT, 2)),
                                List.of(new ItemStack(Items.COAL, 2))),
                        new ItemStack(Asterion.CELESTIAL_STEEL_INGOT), "recipe.asterion.crucible.steel"),
                reaction("celestial_gold", List.of(List.of(new ItemStack(Asterion.CELESTIAL_GOLD_ORE),
                                new ItemStack(Asterion.SHALE_CELESTIAL_GOLD_ORE), new ItemStack(Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE))),
                        new ItemStack(Asterion.CELESTIAL_GOLD_INGOT), "recipe.asterion.crucible.gold"),
                new CrucibleViewerRecipe(Asterion.id("crucible/bonesteel"),
                        List.of(List.of(new ItemStack(Asterion.CELESTIAL_STEEL_INGOT)),
                                List.of(new ItemStack(net.krodark.asterion.game.AncientContent.ANCIENT_BONE, 3))),
                        Asterion.INGOT_CAST, 900,
                        List.of(new ItemStack(Asterion.BONESTEEL_INGOT)),
                        "recipe.asterion.crucible.bonesteel"));
    }

    private static CrucibleViewerRecipe recipe(String name, CrucibleBlockEntity.Mold mold,
                                                Item cast, Item... outputs) {
        return new CrucibleViewerRecipe(Asterion.id("crucible/" + name), List.of(metals()), cast, mold.target(),
                java.util.Arrays.stream(outputs).map(ItemStack::new).toList(),
                "recipe.asterion.crucible.metals");
    }

    private static CrucibleViewerRecipe reaction(String name, List<List<ItemStack>> inputs,
                                                   ItemStack output, String instructionKey) {
        return new CrucibleViewerRecipe(Asterion.id("crucible/" + name), inputs, Asterion.INGOT_CAST,
                name.equals("celestial_steel") ? 700 : CrucibleBlockEntity.Mold.INGOT.target(), List.of(output), instructionKey);
    }

    private static ItemStack sampleIngot(int metal, String name) {
        ItemStack stack = new ItemStack(Asterion.FORGED_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
        net.minecraft.nbt.CompoundTag alloy = new net.minecraft.nbt.CompoundTag();
        alloy.putString("metal_sequence", Integer.toString(metal));
        alloy.putString("alloy", name.substring(0, name.length() - " Ingot".length()));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(alloy));
        return stack;
    }

    private static ItemStack remeltableIngot() {
        ItemStack stack = new ItemStack(Asterion.FORGED_INGOT);
        net.minecraft.nbt.CompoundTag alloy = new net.minecraft.nbt.CompoundTag();
        alloy.putString("metal_sequence", "0");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(alloy));
        return stack;
    }
}

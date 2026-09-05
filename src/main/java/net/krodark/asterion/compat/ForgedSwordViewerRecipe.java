package net.krodark.asterion.compat;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.recipe.ForgedSwordRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.List;

/** Viewer representation for the component-sensitive shapeless sword assembly recipe. */
public record ForgedSwordViewerRecipe(Identifier id, List<ItemStack> inputs, ItemStack output) {
    public static ForgedSwordViewerRecipe create() {
        List<ItemStack> parts = List.of(
                new ItemStack(Asterion.FORGED_SWORD_BLADE),
                new ItemStack(Asterion.FORGED_SWORD_GUARD),
                new ItemStack(Asterion.FORGED_SWORD_POMMEL),
                new ItemStack(Asterion.DEADWOOD_STICK));
        return new ForgedSwordViewerRecipe(Asterion.id("forged_sword_assembly"), parts,
                new ForgedSwordRecipe().assemble(CraftingInput.of(2, 2, parts)));
    }
}

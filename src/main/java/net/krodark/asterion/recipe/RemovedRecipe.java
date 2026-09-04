package net.krodark.asterion.recipe;

import net.krodark.asterion.Asterion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Save-compatible tombstone for recipe IDs removed from older Asterion worlds. */
public final class RemovedRecipe extends CustomRecipe {
    @Override public boolean matches(CraftingInput input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingInput input) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return Asterion.REMOVED_RECIPE; }
}

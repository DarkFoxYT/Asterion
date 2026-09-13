package net.krodark.asterion.recipe;

import net.krodark.asterion.Asterion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

 
public final class RemovedRecipe extends CustomRecipe {
    public RemovedRecipe() { super(net.minecraft.world.item.crafting.CraftingBookCategory.MISC); }
    @Override public boolean matches(CraftingInput input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return Asterion.REMOVED_RECIPE; }
}

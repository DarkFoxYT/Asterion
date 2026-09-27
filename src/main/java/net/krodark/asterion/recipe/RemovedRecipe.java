package net.krodark.asterion.recipe;

import net.krodark.asterion.Asterion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;


public final class RemovedRecipe extends CustomRecipe {
    public RemovedRecipe() {
//? if >=1.20.5 {
super(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
//?} else {
/*super(Asterion.id("removed"), net.minecraft.world.item.crafting.CraftingBookCategory.MISC);*/
//?}
 }
    @Override public boolean matches(CraftingInput input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingInput input,
//? if >=1.20.5 {
net.minecraft.core.HolderLookup.Provider registries
//?} else {
/*net.minecraft.core.RegistryAccess registries*/
//?}
) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() { return Asterion.REMOVED_RECIPE; }

//? if <1.20.5 {
/*public RemovedRecipe(net.minecraft.resources.ResourceLocation id){super(id,net.minecraft.world.item.crafting.CraftingBookCategory.MISC);}*/
//?}
}

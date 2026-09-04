package net.krodark.asterion.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.compat.CrucibleViewerRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@JeiPlugin
public final class AsterionJeiPlugin implements IModPlugin {
    private static final IRecipeType<CrucibleViewerRecipe> CRUCIBLE = IRecipeType.create(
            Asterion.MOD_ID, "crucible_forging", CrucibleViewerRecipe.class);

    @Override public Identifier getPluginUid() {
        return Asterion.id("jei_plugin");
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrucibleCategory(
                registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Asterion.CRUCIBLE)));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CRUCIBLE, CrucibleViewerRecipe.all());
        registration.addItemStackInfo(CrucibleViewerRecipe.metals(),
                Component.translatable("recipe.asterion.crucible.metals"),
                Component.translatable("recipe.asterion.crucible.fuel"),
                Component.translatable("recipe.asterion.crucible.remelting"));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CRUCIBLE, Asterion.CRUCIBLE);
    }

    private record CrucibleCategory(IDrawable icon) implements IRecipeCategory<CrucibleViewerRecipe> {
        @Override public IRecipeType<CrucibleViewerRecipe> getRecipeType() { return CRUCIBLE; }
        @Override public Component getTitle() { return Component.translatable("recipe.asterion.crucible"); }
        @Override public int getWidth() { return 148; }
        @Override public int getHeight() { return 62; }
        @Override public @Nullable IDrawable getIcon() { return icon; }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, CrucibleViewerRecipe recipe,
                                        IFocusGroup focuses) {
            builder.addInputSlot(9, 9).setStandardSlotBackground()
                    .addItemStacks(CrucibleViewerRecipe.metals());
            builder.addInputSlot(39, 9).setStandardSlotBackground().add(recipe.mold());
            builder.addOutputSlot(111, 9).setOutputSlotBackground().addItemStacks(recipe.outputs());
        }

        @Override public void draw(CrucibleViewerRecipe recipe, IRecipeSlotsView slots,
                                   GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, Component.translatable("recipe.asterion.crucible.metals"), 8, 37, 0xFF6B5B4A);
            graphics.text(font, Component.translatable("recipe.asterion.crucible.temperature",
                    recipe.temperature(), CrucibleBlockEntity.TOLERANCE), 8, 50, 0xFF9A3D24);
        }

        @Override public Identifier getIdentifier(CrucibleViewerRecipe recipe) { return recipe.id(); }
    }
}

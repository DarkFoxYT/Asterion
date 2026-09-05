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
import mezz.jei.api.registration.ISubtypeRegistration;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.compat.CrucibleViewerRecipe;
import net.krodark.asterion.compat.ForgedSwordViewerRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

@JeiPlugin
public final class AsterionJeiPlugin implements IModPlugin {
    private static final IRecipeType<CrucibleViewerRecipe> CRUCIBLE = IRecipeType.create(
            Asterion.MOD_ID, "crucible_forging", CrucibleViewerRecipe.class);
    private static final IRecipeType<ForgedSwordViewerRecipe> SWORD_ASSEMBLY = IRecipeType.create(
            Asterion.MOD_ID, "forged_sword_assembly", ForgedSwordViewerRecipe.class);

    @Override public Identifier getPluginUid() {
        return Asterion.id("jei_plugin");
    }

    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(Asterion.FORGED_INGOT, DataComponents.CUSTOM_DATA);
        registration.registerFromDataComponentTypes(Asterion.FORGED_SWORD_GUARD, DataComponents.CUSTOM_DATA);
        registration.registerFromDataComponentTypes(Asterion.FORGED_SWORD_POMMEL, DataComponents.CUSTOM_DATA);
        registration.registerFromDataComponentTypes(Asterion.FORGED_SWORD_BLADE, DataComponents.CUSTOM_DATA);
        registration.registerFromDataComponentTypes(Asterion.FORGED_AXE_HEAD, DataComponents.CUSTOM_DATA);
        registration.registerFromDataComponentTypes(Asterion.FORGED_SWORD, DataComponents.CUSTOM_DATA);
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrucibleCategory(
                registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Asterion.CRUCIBLE)));
        registration.addRecipeCategories(new SwordAssemblyCategory(
                registration.getJeiHelpers().getGuiHelper().createDrawableItemLike(Asterion.FORGED_SWORD)));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CRUCIBLE, CrucibleViewerRecipe.all());
        registration.addRecipes(SWORD_ASSEMBLY, java.util.List.of(ForgedSwordViewerRecipe.create()));
        registration.addItemStackInfo(CrucibleViewerRecipe.metals(),
                Component.translatable("recipe.asterion.crucible.metals"),
                Component.translatable("recipe.asterion.crucible.heat_source"),
                Component.translatable("recipe.asterion.crucible.remelting"));
        registration.addItemStackInfo(new net.minecraft.world.item.ItemStack(Asterion.CRUCIBLE),
                Component.translatable("recipe.asterion.crucible.how_to.heat"),
                Component.translatable("recipe.asterion.crucible.how_to.load"),
                Component.translatable("recipe.asterion.crucible.how_to.cast"));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CRUCIBLE, Asterion.CRUCIBLE);
        registration.addCraftingStation(SWORD_ASSEMBLY, Items.CRAFTING_TABLE);
    }

    private record CrucibleCategory(IDrawable icon) implements IRecipeCategory<CrucibleViewerRecipe> {
        @Override public IRecipeType<CrucibleViewerRecipe> getRecipeType() { return CRUCIBLE; }
        @Override public Component getTitle() { return Component.translatable("recipe.asterion.crucible"); }
        @Override public int getWidth() { return 164; }
        @Override public int getHeight() { return 62; }
        @Override public @Nullable IDrawable getIcon() { return icon; }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, CrucibleViewerRecipe recipe,
                                        IFocusGroup focuses) {
            int materialSlots = recipe.inputs().size();
            for (int index = 0; index < materialSlots; index++)
                builder.addInputSlot(8 + index * 24, 9).setStandardSlotBackground()
                        .addItemStacks(recipe.inputs().get(index));
            builder.addInputSlot(56, 9)
                    .setStandardSlotBackground().add(recipe.mold());
            builder.addInputSlot(80, 9).setStandardSlotBackground()
                    .addItemStacks(CrucibleViewerRecipe.heatSources());
            builder.addOutputSlot(137, 9).setOutputSlotBackground().addItemStacks(recipe.outputs());
        }

        @Override public void draw(CrucibleViewerRecipe recipe, IRecipeSlotsView slots,
                                   GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.text(font, Component.translatable(recipe.instructionKey()), 8, 37, 0xFF6B5B4A, true);
            graphics.text(font, Component.translatable("recipe.asterion.crucible.temperature",
                    recipe.temperature(), CrucibleBlockEntity.TOLERANCE), 8, 50, 0xFF9A3D24, true);
        }

        @Override public Identifier getIdentifier(CrucibleViewerRecipe recipe) { return recipe.id(); }
    }

    private record SwordAssemblyCategory(IDrawable icon) implements IRecipeCategory<ForgedSwordViewerRecipe> {
        @Override public IRecipeType<ForgedSwordViewerRecipe> getRecipeType() { return SWORD_ASSEMBLY; }
        @Override public Component getTitle() {
            return Component.translatable("recipe.asterion.forged_sword_assembly");
        }
        @Override public int getWidth() { return 164; }
        @Override public int getHeight() { return 49; }
        @Override public @Nullable IDrawable getIcon() { return icon; }

        @Override public void setRecipe(IRecipeLayoutBuilder builder, ForgedSwordViewerRecipe recipe,
                                        IFocusGroup focuses) {
            for (int index = 0; index < recipe.inputs().size(); index++)
                builder.addInputSlot(8 + index * 24, 8).setStandardSlotBackground()
                        .add(recipe.inputs().get(index));
            builder.addOutputSlot(137, 8).setOutputSlotBackground().add(recipe.output());
        }

        @Override public void draw(ForgedSwordViewerRecipe recipe, IRecipeSlotsView slots,
                                   GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            graphics.text(Minecraft.getInstance().font,
                    Component.translatable("recipe.asterion.forged_sword_assembly.inherits"),
                    8, 35, 0xFF6B5B4A, true);
        }

        @Override public Identifier getIdentifier(ForgedSwordViewerRecipe recipe) { return recipe.id(); }
    }
}

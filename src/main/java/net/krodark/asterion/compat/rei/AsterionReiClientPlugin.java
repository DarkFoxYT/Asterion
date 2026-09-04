package net.krodark.asterion.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.compat.CrucibleViewerRecipe;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AsterionReiClientPlugin implements REIClientPlugin {
    private static final CategoryIdentifier<CrucibleDisplay> CRUCIBLE =
            CategoryIdentifier.of(Asterion.MOD_ID, "crucible_forging");

    @Override public void registerCategories(CategoryRegistry registry) {
        registry.add(new CrucibleCategory());
        registry.addWorkstations(CRUCIBLE, EntryStacks.of(Asterion.CRUCIBLE));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        for (CrucibleViewerRecipe recipe : CrucibleViewerRecipe.all())
            registry.add(new CrucibleDisplay(recipe));
    }

    private static final class CrucibleDisplay extends BasicDisplay {
        private final int temperature;

        private CrucibleDisplay(CrucibleViewerRecipe recipe) {
            super(List.of(metals(), EntryIngredient.of(EntryStacks.of(recipe.mold()))),
                    List.of(EntryIngredient.of(recipe.outputs().stream().map(EntryStacks::of).toList())),
                    java.util.Optional.of(recipe.id()));
            temperature = recipe.temperature();
        }

        private static EntryIngredient metals() {
            return EntryIngredient.of(CrucibleViewerRecipe.metals().stream().map(EntryStacks::of).toList());
        }

        @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CRUCIBLE; }
        @Override public @Nullable DisplaySerializer<? extends CrucibleDisplay> getSerializer() { return null; }
    }

    private static final class CrucibleCategory implements DisplayCategory<CrucibleDisplay> {
        @Override public CategoryIdentifier<? extends CrucibleDisplay> getCategoryIdentifier() { return CRUCIBLE; }
        @Override public Component getTitle() { return Component.translatable("recipe.asterion.crucible"); }
        @Override public Renderer getIcon() { return EntryStacks.of(Asterion.CRUCIBLE); }
        @Override public int getDisplayHeight() { return 68; }

        @Override public List<Widget> setupDisplay(CrucibleDisplay display, Rectangle bounds) {
            int x = bounds.x, y = bounds.y;
            List<Widget> widgets = new ArrayList<>();
            widgets.add(Widgets.createRecipeBase(bounds));
            widgets.add(Widgets.createSlot(new Point(x + 10, y + 11))
                    .entries(display.getInputEntries().get(0)).markInput());
            widgets.add(Widgets.createSlot(new Point(x + 40, y + 11))
                    .entries(display.getInputEntries().get(1)).markInput());
            widgets.add(Widgets.createArrow(new Point(x + 72, y + 11)));
            widgets.add(Widgets.createSlot(new Point(x + 112, y + 11))
                    .entries(display.getOutputEntries().getFirst()).markOutput());
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), y + 42),
                    Component.translatable("recipe.asterion.crucible.metals")).centered());
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), y + 55),
                    Component.translatable("recipe.asterion.crucible.temperature",
                            display.temperature, CrucibleBlockEntity.TOLERANCE)).centered());
            return widgets;
        }
    }
}

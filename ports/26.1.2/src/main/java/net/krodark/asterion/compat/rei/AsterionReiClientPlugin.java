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
import net.krodark.asterion.compat.ForgedSwordViewerRecipe;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AsterionReiClientPlugin implements REIClientPlugin {
    private static final CategoryIdentifier<CrucibleDisplay> CRUCIBLE =
            CategoryIdentifier.of(Asterion.MOD_ID, "crucible_forging");
    private static final CategoryIdentifier<SwordAssemblyDisplay> SWORD_ASSEMBLY =
            CategoryIdentifier.of(Asterion.MOD_ID, "forged_sword_assembly");

    @Override public void registerCategories(CategoryRegistry registry) {
        registry.add(new CrucibleCategory());
        registry.addWorkstations(CRUCIBLE, EntryStacks.of(Asterion.CRUCIBLE));
        registry.add(new SwordAssemblyCategory());
        registry.addWorkstations(SWORD_ASSEMBLY, EntryStacks.of(net.minecraft.world.item.Items.CRAFTING_TABLE));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        for (CrucibleViewerRecipe recipe : CrucibleViewerRecipe.all())
            registry.add(new CrucibleDisplay(recipe));
        registry.add(new SwordAssemblyDisplay(ForgedSwordViewerRecipe.create()));
    }

    private static final class CrucibleDisplay extends BasicDisplay {
        private final int temperature;
        private final int materialSlots;
        private final String instructionKey;

        private CrucibleDisplay(CrucibleViewerRecipe recipe) {
            super(inputs(recipe),
                    List.of(EntryIngredient.of(recipe.outputs().stream().map(EntryStacks::of).toList())),
                    java.util.Optional.of(recipe.id()));
            temperature = recipe.temperature();
            materialSlots = recipe.inputs().size();
            instructionKey = recipe.instructionKey();
        }

        private static List<EntryIngredient> inputs(CrucibleViewerRecipe recipe) {
            List<EntryIngredient> entries = new ArrayList<>();
            for (List<net.minecraft.world.item.ItemStack> slot : recipe.inputs())
                entries.add(EntryIngredient.of(slot.stream().map(EntryStacks::of).toList()));
            entries.add(EntryIngredient.of(EntryStacks.of(recipe.mold())));
            entries.add(EntryIngredient.of(CrucibleViewerRecipe.heatSources().stream()
                    .map(EntryStacks::of).toList()));
            return entries;
        }

        @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CRUCIBLE; }
        @Override public @Nullable DisplaySerializer<? extends CrucibleDisplay> getSerializer() { return null; }
    }

    private static final class CrucibleCategory implements DisplayCategory<CrucibleDisplay> {
        @Override public CategoryIdentifier<? extends CrucibleDisplay> getCategoryIdentifier() { return CRUCIBLE; }
        @Override public Component getTitle() { return Component.translatable("recipe.asterion.crucible"); }
        @Override public Renderer getIcon() { return EntryStacks.of(Asterion.CRUCIBLE); }
        @Override public int getDisplayHeight() { return 68; }
        @Override public int getDisplayWidth(CrucibleDisplay display) { return 164; }

        @Override public List<Widget> setupDisplay(CrucibleDisplay display, Rectangle bounds) {
            int x = bounds.x, y = bounds.y;
            List<Widget> widgets = new ArrayList<>();
            widgets.add(Widgets.createRecipeBase(bounds));
            for (int index = 0; index < display.materialSlots; index++)
                widgets.add(Widgets.createSlot(new Point(x + 8 + index * 24, y + 11))
                        .entries(display.getInputEntries().get(index)).markInput());
            widgets.add(Widgets.createSlot(new Point(x + 56, y + 11))
                    .entries(display.getInputEntries().get(display.materialSlots)).markInput());
            widgets.add(Widgets.createSlot(new Point(x + 80, y + 11))
                    .entries(display.getInputEntries().get(display.materialSlots + 1)).markInput());
            widgets.add(Widgets.createArrow(new Point(x + 105, y + 11)));
            widgets.add(Widgets.createSlot(new Point(x + 145, y + 11))
                    .entries(display.getOutputEntries().getFirst()).markOutput());
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), y + 42),
                    Component.translatable(display.instructionKey)).centered());
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), y + 55),
                    Component.translatable("recipe.asterion.crucible.temperature",
                            display.temperature, CrucibleBlockEntity.TOLERANCE)).centered());
            return widgets;
        }
    }

    private static final class SwordAssemblyDisplay extends BasicDisplay {
        private SwordAssemblyDisplay(ForgedSwordViewerRecipe recipe) {
            super(recipe.inputs().stream().map(EntryStacks::of).map(EntryIngredient::of).toList(),
                    List.of(EntryIngredient.of(EntryStacks.of(recipe.output()))),
                    java.util.Optional.of(recipe.id()));
        }

        @Override public CategoryIdentifier<?> getCategoryIdentifier() { return SWORD_ASSEMBLY; }
        @Override public @Nullable DisplaySerializer<? extends SwordAssemblyDisplay> getSerializer() { return null; }
    }

    private static final class SwordAssemblyCategory implements DisplayCategory<SwordAssemblyDisplay> {
        @Override public CategoryIdentifier<? extends SwordAssemblyDisplay> getCategoryIdentifier() {
            return SWORD_ASSEMBLY;
        }
        @Override public Component getTitle() {
            return Component.translatable("recipe.asterion.forged_sword_assembly");
        }
        @Override public Renderer getIcon() { return EntryStacks.of(Asterion.FORGED_SWORD); }
        @Override public int getDisplayHeight() { return 55; }
        @Override public int getDisplayWidth(SwordAssemblyDisplay display) { return 164; }

        @Override public List<Widget> setupDisplay(SwordAssemblyDisplay display, Rectangle bounds) {
            int x = bounds.x, y = bounds.y;
            List<Widget> widgets = new ArrayList<>();
            widgets.add(Widgets.createRecipeBase(bounds));
            for (int index = 0; index < display.getInputEntries().size(); index++)
                widgets.add(Widgets.createSlot(new Point(x + 8 + index * 24, y + 9))
                        .entries(display.getInputEntries().get(index)).markInput());
            widgets.add(Widgets.createArrow(new Point(x + 105, y + 9)));
            widgets.add(Widgets.createSlot(new Point(x + 145, y + 9))
                    .entries(display.getOutputEntries().getFirst()).markOutput());
            widgets.add(Widgets.createLabel(new Point(bounds.getCenterX(), y + 39),
                    Component.translatable("recipe.asterion.forged_sword_assembly.inherits")).centered());
            return widgets;
        }
    }
}

package net.krodark.asterion.client.forge;

import net.krodark.asterion.compat.CrucibleViewerRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Predicate;

/** Read-only recipe reference sharing the same recipes as JEI and REI. */
final class ForgeRecipePanel {
    static final int WIDTH = 232, HEIGHT = 144;
    private final List<CrucibleViewerRecipe> all = CrucibleViewerRecipe.all();
    private List<CrucibleViewerRecipe> available = List.of();
    private int page;

    void update(Predicate<Item> ownsMold) {
        var next = all.stream().filter(recipe -> ownsMold.test(recipe.mold())).toList();
        if (!next.equals(available)) {
            var previous = available.isEmpty() ? null : available.get(page);
            available = next;
            page = Math.max(0, available.indexOf(previous));
        }
    }

    boolean click(double mx, double my, int x, int y) {
        if (mx < x || mx >= x + WIDTH || my < y || my >= y + HEIGHT) return false;
        if (my >= y + 5 && my < y + 19 && available.size() > 1) {
            if (mx >= x + 157 && mx < x + 175) page = Math.floorMod(page - 1, available.size());
            if (mx >= x + 208 && mx < x + 226) page = (page + 1) % available.size();
        }
        return true;
    }

    void render(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my,
                int screenMouseX, int screenMouseY, long ticks) {
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF100E0D);
        g.outline(x, y, WIDTH, HEIGHT, 0xFF817361);
        g.fill(x + 1, y + 1, x + WIDTH - 1, y + 23, 0xFF25211D);
        g.text(font, Component.translatable("screen.asterion.forge.recipes"), x + 10, y + 8, 0xFFE1C99F, true);
        if (available.isEmpty()) {
            lines(g, font, Component.translatable("screen.asterion.forge.no_recipes"), x + 12, y + 40, WIDTH - 24);
            return;
        }
        button(g, font, x + 157, y + 5, "‹", mx, my);
        button(g, font, x + 208, y + 5, "›", mx, my);
        String pages = (page + 1) + "/" + available.size();
        g.text(font, pages, x + 191 - font.width(pages) / 2, y + 8, 0xFFCFB993, true);
        var recipe = available.get(page);
        ItemStack output = recipe.outputs().getFirst();
        String title = output.getHoverName().getString();
        g.text(font, font.plainSubstrByWidth(title, WIDTH - 24), x + 12, y + 30, 0xFFE1C99F, true);
        g.text(font, font.plainSubstrByWidth(new ItemStack(recipe.mold()).getHoverName().getString(), WIDTH - 24),
                x + 12, y + 43, 0xFFAD9B80, true);
        int slotX = x + 12;
        for (var alternatives : recipe.inputs()) {
            ItemStack ingredient = alternatives.get((int)Math.floorMod(ticks / 40, alternatives.size()));
            slot(g, font, ingredient, slotX, y + 60, mx, my, screenMouseX, screenMouseY);
            slotX += 37;
            if (slotX < x + 12 + recipe.inputs().size() * 37)
                g.text(font, "+", slotX - 10, y + 67, 0xFFAD9B80, true);
        }
        g.text(font, "→", x + 177, y + 67, 0xFFCFB993, true);
        slot(g, font, output, x + 198, y + 60, mx, my, screenMouseX, screenMouseY);
        lines(g, font, Component.translatable(recipe.instructionKey()), x + 12, y + 90, WIDTH - 24);
        int tolerance = recipe.mold() == net.krodark.asterion.Asterion.MINOTAUR_KEY_CAST ? 20 : recipe.temperature() >= 700 ? 8 : 12;
        g.fill(x + 10, y + 124, x + WIDTH - 10, y + 125, 0xFF554A3D);
        g.text(font, Component.translatable("recipe.asterion.crucible.temperature", recipe.temperature(), tolerance),
                x + 12, y + 131, 0xFFE5B77B, true);
        g.text(font, "?", x + WIDTH - 18, y + 131, 0xFFAD9B80, true);
        if (mx >= x + WIDTH - 23 && mx < x + WIDTH - 7 && my >= y + 126 && my < y + HEIGHT)
            g.setTooltipForNextFrame(font, Component.translatable("recipe.asterion.crucible.heat_source"), screenMouseX, screenMouseY);
    }

    private static void lines(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width) {
        for (var line : font.split(text, width)) {
            g.text(font, line, x, y, 0xFFC5B69F, true);
            y += 10;
        }
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, String text, int mx, int my) {
        g.fill(x, y, x + 18, y + 14, 0xFF181513);
        g.outline(x, y, 18, 14, mx >= x && mx < x + 18 && my >= y && my < y + 14 ? 0xFFD0B68C : 0xFF554A3D);
        g.text(font, text, x + 9 - font.width(text) / 2, y + 3, 0xFFCFB993, true);
    }

    private static void slot(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y,
                             int mx, int my, int screenMouseX, int screenMouseY) {
        g.fill(x, y, x + 22, y + 22, 0xFF29231E);
        g.outline(x, y, 22, 22, 0xFF554A3D);
        g.item(stack, x + 3, y + 3);
        g.itemDecorations(font, stack, x + 3, y + 3);
        if (mx >= x && mx < x + 22 && my >= y && my < y + 22)
            g.setTooltipForNextFrame(font, stack, screenMouseX, screenMouseY);
    }
}

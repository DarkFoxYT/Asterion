package net.krodark.asterion.update;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class UpdateTexture {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("asterion", "textures/gui/update_gui.png");

    private static final Identifier TITLE = TEXTURE.withPath("textures/gui/asterion_text_title.png");

    private UpdateTexture() {}

    static void title(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TITLE, x, y, 0F, 0F, width, height, 652, 128, 652, 128);
    }

    static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF282828);
        int[] sourceX = {0, 4, 52}, sourceY = {0, 4, 24};
        int[] sourceW = {4, 48, 4}, sourceH = {4, 20, 4};
        int[] targetW = {4, width - 8, 4}, targetH = {4, height - 8, 4};
        int py = y;
        for (int row = 0; row < 3; row++) {
            int px = x;
            for (int col = 0; col < 3; col++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, px, py,
                        (float) sourceX[col], (float) sourceY[row], targetW[col], targetH[row],
                        sourceW[col], sourceH[row], 56, 28);
                px += targetW[col];
            }
            py += targetH[row];
        }
    }
}

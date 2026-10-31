package net.krodark.asterion.update;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class UpdateTexture {
    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse("asterion:textures/gui/update_gui.png");

    private static final ResourceLocation TITLE = TEXTURE.withPath("textures/gui/asterion_text_title.png");

    private UpdateTexture() {}

    static void title(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(TITLE, x, y, width, height, 0F, 0F, 652, 128, 652, 128);
    }

    static void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF282828);
        int[] sourceX = {0, 4, 52}, sourceY = {0, 4, 24};
        int[] sourceW = {4, 48, 4}, sourceH = {4, 20, 4};
        int[] targetW = {4, width - 8, 4}, targetH = {4, height - 8, 4};
        int py = y;
        for (int row = 0; row < 3; row++) {
            int px = x;
            for (int col = 0; col < 3; col++) {
                graphics.blit(TEXTURE, px, py, targetW[col], targetH[row],
                        (float) sourceX[col], (float) sourceY[row],
                        sourceW[col], sourceH[row], 56, 28);
                px += targetW[col];
            }
            py += targetH[row];
        }
    }
}

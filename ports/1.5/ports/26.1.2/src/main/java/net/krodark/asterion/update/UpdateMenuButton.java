package net.krodark.asterion.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdateMenuButton extends Button {
    private final boolean dismiss;

    public UpdateMenuButton(Screen parent, int x, boolean dismiss) {
        super(x, 2, dismiss ? 22 : 200, 26,
                Component.literal(dismiss ? "Dismiss Asterion update" : "View Asterion update"), clicked -> {
                    if (dismiss) UpdateNotifications.dismiss();
                    else {
                        var notice = UpdateNotifications.available();
                        if (notice != null) Minecraft.getInstance().setScreen(new UpdateScreen(parent, notice));
                    }
                }, DEFAULT_NARRATION);
        this.dismiss = dismiss;
        visible = active = UpdateNotifications.available() != null;
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        var notice = UpdateNotifications.available();
        if (notice == null) return;
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        UpdateTexture.draw(graphics, x, y, width, height);
        if (isHoveredOrFocused()) graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x22FFFFFF);
        String text = dismiss ? "x" : "Asterion " + notice.version() + " available";
        graphics.text(font, text, x + (width - font.width(text)) / 2, y + 9,
                isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFE0E0E0, true);
    }
}

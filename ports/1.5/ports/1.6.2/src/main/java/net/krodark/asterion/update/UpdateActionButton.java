package net.krodark.asterion.update;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class UpdateActionButton extends Button {
    UpdateActionButton(int x, int y, int width, int height, Component message, OnPress action) {
        super(x, y, width, height, message, action, DEFAULT_NARRATION);
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        UpdateTexture.draw(graphics, getX(), getY(), width, height);
        if (isHoveredOrFocused()) graphics.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0x22FFFFFF);
        var font = Minecraft.getInstance().font;
        graphics.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                getY() + (height - 8) / 2, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFD0D0D0, true);
    }
}

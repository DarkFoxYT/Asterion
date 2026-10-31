package net.krodark.asterion.update;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.net.URI;

public final class UpdateScreen extends Screen {
    private final Screen parent;
    private final UpdateNotifications.Notice notice;
    private int left, top, panelWidth;

    public UpdateScreen(Screen parent, UpdateNotifications.Notice notice) {
        super(Component.literal("Asterion update"));
        this.parent = parent;
        this.notice = notice;
    }

    @Override protected void init() {
        panelWidth = Math.min(320, width - 24);
        left = (width - panelWidth) / 2;
        top = (height - 204) / 2;
        addRenderableWidget(new UpdateActionButton(left + 16, top + 140, panelWidth - 32, 20,
                Component.literal("Open CurseForge"), button ->
                net.minecraft.util.Util.getPlatform().openUri(URI.create(notice.url()))));
        int half = (panelWidth - 38) / 2;
        addRenderableWidget(new UpdateActionButton(left + 16, top + 168, half, 20,
                Component.literal("Later"), button -> onClose()));
        addRenderableWidget(new UpdateActionButton(left + 22 + half, top + 168, half, 20,
                Component.literal("Skip this version"), button -> {
            UpdateNotifications.dismiss();
            onClose();
        }));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        parent.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.fill(0, 0, width, height, 0xBB000000);
        UpdateTexture.draw(graphics, left, top, panelWidth, 204);
        graphics.fillGradient(left + 4, top + 4, left + panelWidth - 4, top + 200, 0xFF343434, 0xFF242424);
        int titleWidth = Math.min(244, panelWidth - 32);
        int titleHeight = Math.round(titleWidth * 128F / 652F);
        UpdateTexture.title(graphics, left + (panelWidth - titleWidth) / 2, top + 10, titleWidth, titleHeight);

        centered(graphics, "Version " + notice.version() + " is available.", top + 74, 0xFFE0E0E0);
        String loader = notice.loader().equals("neoforge") ? "NeoForge" : notice.loader().equals("forge") ? "Forge"
                : notice.loader().equals("quilt") ? "Quilt" : "Fabric";
        centered(graphics, "Minecraft " + notice.minecraft() + " / " + loader, top + 110, 0xFFB0B0B0);
        centered(graphics, "Installed: " + notice.installed(), top + 92, 0xFFB0B0B0);
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.text(font, text, left + (panelWidth - font.width(text)) / 2, y, color, true);
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}

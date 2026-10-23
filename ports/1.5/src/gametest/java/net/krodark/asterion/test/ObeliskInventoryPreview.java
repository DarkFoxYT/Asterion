package net.krodark.asterion.test;

import net.krodark.asterion.block.RespawnObelisks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ObeliskInventoryPreview extends Screen {
    private static Screen previous;
    private ObeliskInventoryPreview() { super(Component.literal("Obelisk inventory model")); }
    static void show(Minecraft client) { previous = client.screen; client.setScreen(new ObeliskInventoryPreview()); }
    static void capture(Minecraft client) {
        if (!(client.screen instanceof ObeliskInventoryPreview)) throw new AssertionError("Obelisk preview did not render");
        net.minecraft.client.Screenshot.grab(client.gameDirectory, "asterion-obelisk-inventory.png",
                client.getMainRenderTarget(), message -> {});
        client.setScreen(previous);
        previous = null;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF25282E);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 86, 0xFFF0EEE5);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F - 64, height / 2F - 64, 0);
        graphics.pose().scale(8, 8, 8);
        graphics.renderItem(new ItemStack(RespawnObelisks.OBELISK), 0, 0);
        graphics.pose().popPose();
    }
}

package net.krodark.asterion.port.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Credits render independently of F1/hidden cinematic HUD and hold input until the return fade. */
public final class PortBossCreditsScreen extends Screen {
    public PortBossCreditsScreen(){super(Component.literal("Asterion credits"));}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        PortBossFinaleOverlay.renderOverlay(graphics,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
}

package net.krodark.asterion.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Credits render independently of F1/hidden cinematic HUD and hold input until the return fade. */
public final class BossCreditsScreen extends Screen {
    public BossCreditsScreen(){super(Component.literal("Asterion credits"));}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial) {
        BossFinaleOverlay.renderOverlay(graphics,Minecraft.getInstance().getDeltaTracker());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean shouldCloseOnEsc(){return false;}
}

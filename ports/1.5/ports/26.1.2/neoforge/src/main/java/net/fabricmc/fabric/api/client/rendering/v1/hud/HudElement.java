package net.fabricmc.fabric.api.client.rendering.v1.hud;

@FunctionalInterface
public interface HudElement {
    void extractRenderState(
            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker tracker);
}

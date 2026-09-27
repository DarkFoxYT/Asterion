package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface HudRenderCallback {
    List<HudRenderCallback> LISTENERS = new ArrayList<>();
    Event<HudRenderCallback> EVENT = LISTENERS::add;
    void onHudRender(GuiGraphics graphics, DeltaTracker delta);
    static void fire(GuiGraphics graphics, DeltaTracker delta) {
        LISTENERS.forEach(it -> it.onHudRender(graphics, delta));
    }
}

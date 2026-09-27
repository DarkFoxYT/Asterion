package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers shared HUD layers with Forge's overlay layer event. */
public final class HudElementRegistry {
    private static final Map<Identifier, HudElement> ELEMENTS = new LinkedHashMap<>();

    static {
        AddGuiOverlayLayersEvent.BUS.addListener(event -> ELEMENTS.forEach((id, element) ->
                event.getLayeredDraw().add(id, element::extractRenderState)));
    }

    private HudElementRegistry() {}

    public static void addLast(Identifier id, HudElement element) {
        ELEMENTS.put(id, element);
    }
}

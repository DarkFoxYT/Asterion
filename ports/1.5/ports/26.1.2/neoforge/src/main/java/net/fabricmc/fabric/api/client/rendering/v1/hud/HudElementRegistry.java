package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

public final class HudElementRegistry {
    private static final java.util.Map<Identifier, HudElement> ELEMENTS =
            new java.util.LinkedHashMap<>();

    private HudElementRegistry() {}

    public static void addLast(Identifier id, HudElement element) {
        ELEMENTS.put(id, element);
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        ELEMENTS.forEach((id, element) -> event.registerAboveAll(id, element::extractRenderState));
    }
}

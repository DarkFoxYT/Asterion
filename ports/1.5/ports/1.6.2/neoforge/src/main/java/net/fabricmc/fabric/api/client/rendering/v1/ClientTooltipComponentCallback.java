package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public interface ClientTooltipComponentCallback {
    java.util.List<ClientTooltipComponentCallback> LISTENERS = new java.util.ArrayList<>();
    Event<ClientTooltipComponentCallback> EVENT = new Event<>(LISTENERS::add);

    ClientTooltipComponent getComponent(TooltipComponent component);

    static void register(
            net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
                    event) {
        event.register(
                net.krodark.asterion.item.ForgedTooltipComponent.class,
                component -> {
                    for (var callback : LISTENERS) {
                        var result = callback.getComponent(component);
                        if (result != null) return result;
                    }
                    throw new IllegalStateException("No forged tooltip component factory");
                });
    }
}

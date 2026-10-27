package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

@FunctionalInterface
public interface ClientTooltipComponentCallback {
    Registration EVENT = new Registration();
    ClientTooltipComponent getComponent(TooltipComponent component);
    final class Registration {
        private final java.util.List<ClientTooltipComponentCallback> callbacks = new java.util.ArrayList<>();
        Registration() {
            net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent.BUS.addListener(event ->
                    event.register(net.krodark.asterion.item.ForgedTooltipComponent.class, component -> {
                        for (var callback : callbacks) {
                            var result = callback.getComponent(component);
                            if (result != null) return result;
                        }
                        throw new IllegalStateException("No forged tooltip component factory");
                    }));
        }
        public void register(ClientTooltipComponentCallback callback) { callbacks.add(callback); }
    }
}
